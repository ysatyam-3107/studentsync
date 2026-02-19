package com.example.studysync;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotesActivity extends AppCompatActivity {

    private static final String TAG = "NotesActivity";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private RecyclerView rvNotes;
    private Button btnUploadNote;
    private ProgressBar progressBar;

    private NotesAdapter notesAdapter;
    private List<Note> notesList;

    private String roomCode;
    private DatabaseReference notesRef;
    private FirebaseAuth auth;

    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        // ✅ Initialize Cloudinary
        initCloudinary();

        rvNotes = findViewById(R.id.rvNotes);
        btnUploadNote = findViewById(R.id.btnUploadNote);
        progressBar = findViewById(R.id.progressBarNotes);

        roomCode = getIntent().getStringExtra("roomCode");
        auth = FirebaseAuth.getInstance();

        if (roomCode == null || auth.getCurrentUser() == null) {
            Toast.makeText(this, "Invalid session", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        notesRef = FirebaseDatabase.getInstance()
                .getReference("Notes").child(roomCode);

        notesList = new ArrayList<>();
        notesAdapter = new NotesAdapter(this, notesList,
                new NotesAdapter.OnNoteClickListener() {
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

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK &&
                            result.getData() != null &&
                            result.getData().getData() != null) {

                        Uri uri = result.getData().getData();

                        try {
                            getContentResolver().takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );
                        } catch (SecurityException e) {
                            Log.w(TAG, "Could not persist URI permission", e);
                        }

                        uploadFile(uri);
                    }
                }
        );

        loadNotes();

        btnUploadNote.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/plain",
                    "image/*"
            });
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            filePickerLauncher.launch(intent);
        });
    }

    /**
     * Initialize Cloudinary with your credentials
     */
    /**
     * Initialize Cloudinary with your credentials
     */
    private void initCloudinary() {
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", CloudinaryConfig.CLOUD_NAME);
        config.put("api_key", CloudinaryConfig.API_KEY);
        config.put("api_secret", CloudinaryConfig.API_SECRET);

        try {
            MediaManager.init(this, config);
            Log.d(TAG, "✅ Cloudinary initialized successfully");
            Log.d(TAG, "Cloud Name: " + CloudinaryConfig.CLOUD_NAME);
        } catch (Exception e) {
            Log.e(TAG, "❌ Cloudinary initialization failed", e);
            Toast.makeText(this,
                    "Cloud storage setup failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void loadNotes() {
        notesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                notesList.clear();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    Note note = snap.getValue(Note.class);
                    if (note != null) {
                        note.setId(snap.getKey());
                        notesList.add(note);
                    }
                }
                notesAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load notes", error.toException());
                Toast.makeText(NotesActivity.this,
                        "Failed to load notes: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void uploadFile(Uri fileUri) {
        Log.d(TAG, "=== STARTING CLOUDINARY UPLOAD ===");

        // Validate file size
        long fileSize = getFileSize(fileUri);
        Log.d(TAG, "File size: " + fileSize + " bytes");

        if (fileSize > MAX_FILE_SIZE) {
            Toast.makeText(this,
                    "File too large. Maximum size is 10MB",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (fileSize == -1) {
            Toast.makeText(this,
                    "Could not read file. Please try again",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnUploadNote.setEnabled(false);

        String tempFileName = getFileName(fileUri);
        final String fileName = (tempFileName != null)
                ? tempFileName
                : "file_" + System.currentTimeMillis();

        Log.d(TAG, "File name: " + fileName);

        String noteId = notesRef.push().getKey();
        if (noteId == null) {
            progressBar.setVisibility(View.GONE);
            btnUploadNote.setEnabled(true);
            Toast.makeText(this, "Failed to generate note ID", Toast.LENGTH_SHORT).show();
            return;
        }

        String fileType = detectFileType(fileUri, fileName);
        Log.d(TAG, "File type: " + fileType);

        // Upload to Cloudinary
        String folderPath = "studysync/" + roomCode;

        MediaManager.get().upload(fileUri)
                .option("resource_type", "auto")  // Auto-detect file type
                .option("folder", folderPath)     // Organize by room
                .option("public_id", noteId + "_" + getFileNameWithoutExtension(fileName))
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                        Log.d(TAG, "Upload started - Request ID: " + requestId);
                        runOnUiThread(() ->
                                Toast.makeText(NotesActivity.this,
                                        "Uploading...", Toast.LENGTH_SHORT).show()
                        );
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                        int progress = (int) ((bytes * 100) / totalBytes);
                        Log.d(TAG, "Upload progress: " + progress + "%");
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String fileUrl = (String) resultData.get("secure_url");
                        Log.d(TAG, "✅ Upload successful! URL: " + fileUrl);

                        runOnUiThread(() ->
                                saveNoteToDatabase(noteId, fileName, fileUrl, fileType)
                        );
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Log.e(TAG, "❌ Upload error: " + error.getDescription());

                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            btnUploadNote.setEnabled(true);
                            Toast.makeText(NotesActivity.this,
                                    "Upload failed: " + error.getDescription(),
                                    Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                        Log.w(TAG, "Upload rescheduled: " + error.getDescription());
                    }
                })
                .dispatch();
    }

    private void saveNoteToDatabase(String noteId, String fileName,
                                    String fileUrl, String fileType) {
        Log.d(TAG, "=== SAVING TO DATABASE ===");

        String uploaderId = auth.getCurrentUser().getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("Users").child(uploaderId);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String uploaderName = snapshot.child("name").getValue(String.class);
                if (uploaderName == null) uploaderName = "User";

                Map<String, Object> data = new HashMap<>();
                data.put("fileName", fileName);
                data.put("fileUrl", fileUrl);
                data.put("uploaderId", uploaderId);
                data.put("uploaderName", uploaderName);
                data.put("uploadedAt", ServerValue.TIMESTAMP);
                data.put("fileType", fileType);

                notesRef.child(noteId).setValue(data)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "✅ Note saved to database");
                            progressBar.setVisibility(View.GONE);
                            btnUploadNote.setEnabled(true);
                            Toast.makeText(NotesActivity.this,
                                    "Note uploaded successfully!",
                                    Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "❌ Database save failed", e);
                            progressBar.setVisibility(View.GONE);
                            btnUploadNote.setEnabled(true);
                            Toast.makeText(NotesActivity.this,
                                    "Failed to save: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "❌ Failed to get user info", error.toException());
                progressBar.setVisibility(View.GONE);
                btnUploadNote.setEnabled(true);
            }
        });
    }

    private void downloadNote(Note note) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(note.getFileUrl()));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Download error", e);
            Toast.makeText(this,
                    "Cannot open file: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteNote(Note note) {
        if (!note.getUploaderId().equals(auth.getCurrentUser().getUid())) {
            Toast.makeText(this,
                    "Only uploader can delete",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Delete from database
        notesRef.child(note.getId()).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this,
                            "Note deleted successfully",
                            Toast.LENGTH_SHORT).show();

                    // Note: Cloudinary file deletion requires admin API
                    // For now, files remain in Cloudinary (won't affect functionality)
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this,
                            "Failed to delete: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private String getFileName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    return cursor.getString(nameIndex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "File name error", e);
        }
        return null;
    }

    private String getFileNameWithoutExtension(String fileName) {
        if (fileName == null) return "file";
        int lastDot = fileName.lastIndexOf('.');
        return (lastDot > 0) ? fileName.substring(0, lastDot) : fileName;
    }

    private long getFileSize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1) {
                    return cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "File size error", e);
        }
        return -1;
    }

    private String detectFileType(Uri uri, String fileName) {
        String mimeType = getContentResolver().getType(uri);

        if (mimeType != null) {
            if (mimeType.contains("pdf")) return "pdf";
            else if (mimeType.contains("text")) return "text";
            else if (mimeType.contains("word") || mimeType.contains("document")) return "document";
            else if (mimeType.contains("image")) return "image";
        }

        if (fileName != null) {
            String extension = "";
            int lastDot = fileName.lastIndexOf('.');
            if (lastDot > 0) {
                extension = fileName.substring(lastDot + 1).toLowerCase();
            }

            switch (extension) {
                case "pdf": return "pdf";
                case "txt":
                case "text": return "text";
                case "doc":
                case "docx": return "document";
                case "jpg":
                case "jpeg":
                case "png":
                case "gif": return "image";
                default: return "other";
            }
        }

        return "other";
    }
}