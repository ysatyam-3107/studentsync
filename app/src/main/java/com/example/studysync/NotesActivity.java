package com.example.studysync;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotesActivity extends AppCompatActivity {

    private Button btnUploadNote;
    private ProgressBar progressBar;

    private NotesAdapter notesAdapter;
    private List<Note> notesList;

    private DatabaseReference notesRef;
    private StorageReference storageRef;
    private FirebaseAuth auth;

    private ActivityResultLauncher<String> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        // Initialize views
        RecyclerView rvNotes = findViewById(R.id.rvNotes);
        btnUploadNote = findViewById(R.id.btnUploadNote);
        progressBar = findViewById(R.id.progressBarNotes);

        // Get room code
        String roomCode = getIntent().getStringExtra("roomCode");
        auth = FirebaseAuth.getInstance();

        if (roomCode == null || auth.getCurrentUser() == null) {
            Toast.makeText(this, "Invalid session", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Firebase references
        notesRef = FirebaseDatabase.getInstance()
                .getReference("Notes").child(roomCode);
        storageRef = FirebaseStorage.getInstance()
                .getReference("notes").child(roomCode);

        // Setup RecyclerView
        notesList = new ArrayList<>();
        notesAdapter = new NotesAdapter(this, notesList, new NotesAdapter.OnNoteClickListener() {
            @Override
            public void onDownloadClick(Note note) {
                downloadNote(note);
            }

            @Override
            public void onDeleteClick(Note note) {
                deleteNote(note);
            }
        });

        rvNotes.setLayoutManager(new LinearLayoutManager(this));
        rvNotes.setAdapter(notesAdapter);

        // File picker launcher
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        uploadFile(uri);
                    }
                }
        );

        // Load notes
        loadNotes();

        // Upload button
        btnUploadNote.setOnClickListener(v -> filePickerLauncher.launch("*/*"));
    }

    private void loadNotes() {
        notesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                notesList.clear();
                for (DataSnapshot noteSnap : snapshot.getChildren()) {
                    Note note = noteSnap.getValue(Note.class);
                    if (note != null) {
                        note.setId(noteSnap.getKey());
                        notesList.add(note);
                    }
                }
                notesAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(NotesActivity.this,
                        "Failed to load notes", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void uploadFile(Uri fileUri) {
        progressBar.setVisibility(View.VISIBLE);
        btnUploadNote.setEnabled(false);

        String fileName = getFileName(fileUri);
        String noteId = notesRef.push().getKey();
        if (noteId == null) return;

        StorageReference fileRef = storageRef.child(noteId + "_" + fileName);

        fileRef.putFile(fileUri)
                .addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl()
                        .addOnSuccessListener(downloadUri -> {
                            saveNoteToDatabase(noteId, fileName, downloadUri.toString());
                        })
                        .addOnFailureListener(e -> {
                            progressBar.setVisibility(View.GONE);
                            btnUploadNote.setEnabled(true);
                            Toast.makeText(this, "Failed to get download URL",
                                    Toast.LENGTH_SHORT).show();
                        }))
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnUploadNote.setEnabled(true);
                    Toast.makeText(this, "Upload failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void saveNoteToDatabase(String noteId, String fileName, String fileUrl) {
        String uploaderId = auth.getCurrentUser().getUid();
        String uploaderName = auth.getCurrentUser().getEmail();

        Map<String, Object> noteData = new HashMap<>();
        noteData.put("fileName", fileName);
        noteData.put("fileUrl", fileUrl);
        noteData.put("uploaderId", uploaderId);
        noteData.put("uploaderName", uploaderName);
        noteData.put("uploadedAt", ServerValue.TIMESTAMP);
        noteData.put("fileType", getFileType(fileName));

        notesRef.child(noteId).setValue(noteData)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    btnUploadNote.setEnabled(true);
                    Toast.makeText(this, "Note uploaded successfully",
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnUploadNote.setEnabled(true);
                    Toast.makeText(this, "Failed to save note info",
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void downloadNote(Note note) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(note.getFileUrl()));
        startActivity(browserIntent);
        Toast.makeText(this, "Opening " + note.getFileName(), Toast.LENGTH_SHORT).show();
    }

    private void deleteNote(Note note) {
        // Only uploader can delete
        if (!note.getUploaderId().equals(auth.getCurrentUser().getUid())) {
            Toast.makeText(this, "Only uploader can delete this note",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Delete from database
        notesRef.child(note.getId()).removeValue()
                .addOnSuccessListener(aVoid -> {
                    // Delete from storage
                    String fileName = note.getId() + "_" + note.getFileName();
                    storageRef.child(fileName).delete();
                    Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show());
    }

    private String getFileName(Uri uri) {
        String path = uri.getPath();
        if (path != null) {
            int index = path.lastIndexOf('/');
            if (index != -1) {
                return path.substring(index + 1);
            }
        }
        return "file_" + System.currentTimeMillis();
    }

    private String getFileType(String fileName) {
        if (fileName.toLowerCase().endsWith(".pdf")) {
            return "pdf";
        } else if (fileName.toLowerCase().endsWith(".txt")) {
            return "text";
        } else if (fileName.toLowerCase().endsWith(".doc") ||
                fileName.toLowerCase().endsWith(".docx")) {
            return "doc";
        }
        return "other";
    }
}