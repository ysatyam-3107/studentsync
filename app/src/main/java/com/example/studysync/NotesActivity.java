package com.example.studysync;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
    private final Map<Long, Note> downloadMap = new HashMap<>();
    private BroadcastReceiver downloadReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        rvNotes       = findViewById(R.id.rvNotes);
        btnUploadNote = findViewById(R.id.btnUploadNote);
        progressBar   = findViewById(R.id.progressBarNotes);

        roomCode = getIntent().getStringExtra("roomCode");
        auth     = FirebaseAuth.getInstance();

        if (roomCode == null || auth.getCurrentUser() == null) {
            Toast.makeText(this, "Invalid session", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        notesRef = FirebaseDatabase.getInstance().getReference("Notes").child(roomCode);

        notesList    = new ArrayList<>();
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

        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadMap.containsKey(downloadId)) {
                    Note note = downloadMap.get(downloadId);
                    downloadMap.remove(downloadId);
                    openDownloadedFile(downloadId, note);
                }
            }
        };

        // ACTION_DOWNLOAD_COMPLETE is sent by the system DownloadManager (outside our app),
        // so we MUST use RECEIVER_EXPORTED on Android 13+ — NOT_EXPORTED would block it.
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, filter);
        }

        // ── File picker ───────────────────────────────────────────────────────
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK
                            && result.getData() != null
                            && result.getData().getData() != null) {
                        Uri uri = result.getData().getData();
                        try {
                            getContentResolver().takePersistableUriPermission(
                                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException e) {
                            Log.w(TAG, "Could not persist URI permission", e);
                        }
                        uploadFile(uri);
                    }
                });

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (downloadReceiver != null) {
            unregisterReceiver(downloadReceiver);
        }
    }

    // ─── Load notes ───────────────────────────────────────────────────────────

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
                // FIX: Use notifyItemRangeChanged instead of notifyDataSetChanged
                notesAdapter.notifyItemRangeChanged(0, notesList.size());
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

    // ─── Upload ───────────────────────────────────────────────────────────────

    private void uploadFile(Uri fileUri) {
        Log.d(TAG, "=== STARTING CLOUDINARY UPLOAD ===");

        long fileSize = getFileSize(fileUri);

        if (fileSize > MAX_FILE_SIZE) {
            Toast.makeText(this, "File too large. Maximum size is 10MB",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (fileSize == -1) {
            Toast.makeText(this, "Could not read file. Please try again",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnUploadNote.setEnabled(false);

        String tempFileName = getFileName(fileUri);
        String fileType     = detectFileType(fileUri, tempFileName);
        final String fileName = (tempFileName != null && !tempFileName.isEmpty())
                ? tempFileName : ("file_" + System.currentTimeMillis());

        MediaManager.get().upload(fileUri)
                .option("resource_type", "raw")
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                        Log.d(TAG, "Upload started: " + requestId);
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                        Log.d(TAG, "Upload progress: " + bytes + "/" + totalBytes);
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String url = (String) resultData.get("secure_url");
                        Log.d(TAG, "Upload success: " + url);

                        // FIX: null-safe UID access
                        String uid = (auth.getCurrentUser() != null)
                                ? auth.getCurrentUser().getUid() : "unknown";

                        Map<String, Object> noteData = new HashMap<>();
                        noteData.put("fileName",   fileName);
                        noteData.put("fileUrl",    url);
                        noteData.put("fileType",   fileType);
                        noteData.put("uploaderId", uid);
                        noteData.put("timestamp",  ServerValue.TIMESTAMP);

                        notesRef.push().setValue(noteData)
                                .addOnSuccessListener(aVoid -> {
                                    runOnUiThread(() -> {
                                        progressBar.setVisibility(View.GONE);
                                        btnUploadNote.setEnabled(true);
                                        Toast.makeText(NotesActivity.this,
                                                "Note uploaded successfully",
                                                Toast.LENGTH_SHORT).show();
                                    });
                                })
                                .addOnFailureListener(e -> {
                                    runOnUiThread(() -> {
                                        progressBar.setVisibility(View.GONE);
                                        btnUploadNote.setEnabled(true);
                                        Toast.makeText(NotesActivity.this,
                                                "Failed to save note: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                    });
                                });
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Log.e(TAG, "Upload error: " + error.getDescription());
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
                }).dispatch();
    }

    // ─── Download ─────────────────────────────────────────────────────────────

    private void downloadNote(Note note) {
        if (note.getFileUrl() == null || note.getFileUrl().isEmpty()) {
            Toast.makeText(this, "Invalid file URL", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String extension = getExtensionFromFileType(note.getFileType());
            String baseName  = getFileNameWithoutExtension(note.getFileName());
            String dlName    = baseName + extension;

            DownloadManager.Request request = new DownloadManager.Request(
                    Uri.parse(note.getFileUrl()));
            request.setTitle(note.getFileName());
            request.setDescription("Downloading from StudySync");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, dlName);
            request.setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI |
                            DownloadManager.Request.NETWORK_MOBILE);
            request.setAllowedOverRoaming(true);
            request.setAllowedOverMetered(true);

            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            long downloadId = dm.enqueue(request);
            downloadMap.put(downloadId, note);

            Log.d(TAG, "Download started - ID: " + downloadId);

        } catch (Exception e) {
            Log.e(TAG, "Download error", e);
            Toast.makeText(this, "Download failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openDownloadedFile(long downloadId, Note note) {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);

            Cursor cursor = dm.query(query);
            if (cursor == null || !cursor.moveToFirst()) {
                if (cursor != null) cursor.close();
                Toast.makeText(this, "File saved to Downloads folder", Toast.LENGTH_SHORT).show();
                return;
            }

            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int status = cursor.getInt(statusIndex);
            cursor.close();

            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                Toast.makeText(this, "Download failed. Please try again.", Toast.LENGTH_SHORT).show();
                return;
            }

            String mime = getMimeType(note.getFileType());

            // On Android 13+, content://downloads/public_downloads is restricted.
            // Best approach: query MediaStore for the file by name in Downloads,
            // which gives a proper shareable content URI.
            Uri fileUri = getDownloadedFileUri(note.getFileName(), mime);

            if (fileUri != null) {
                openFileWithUri(fileUri, mime, note.getFileName());
            } else {
                // Fallback 1: try the legacy downloads content URI (works on some devices)
                try {
                    Uri legacyUri = android.content.ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"), downloadId);
                    openFileWithUri(legacyUri, mime, note.getFileName());
                } catch (Exception e) {
                    Log.e(TAG, "Legacy URI failed too", e);
                    // Fallback 2: just open the Downloads folder
                    openDownloadsFolder();
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "openDownloadedFile error", e);
            openDownloadsFolder();
        }
    }

    /**
     * Queries MediaStore Downloads collection (Android 10+) for the file by display name.
     * Returns a content URI that can be safely passed to any app via Intent.
     */
    private Uri getDownloadedFileUri(String fileName, String mime) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ — use MediaStore.Downloads
                Uri collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                String[] projection = {android.provider.MediaStore.Downloads._ID};
                String selection = android.provider.MediaStore.Downloads.DISPLAY_NAME + " = ?";

                // Try exact name first, then name without extension
                String[] selectionArgs = {fileName};
                try (Cursor cursor = getContentResolver().query(
                        collection, projection, selection, selectionArgs, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        long id = cursor.getLong(
                                cursor.getColumnIndexOrThrow(android.provider.MediaStore.Downloads._ID));
                        return android.content.ContentUris.withAppendedId(collection, id);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "MediaStore query failed", e);
        }
        return null;
    }

    private void openFileWithUri(Uri fileUri, String mime, String fileName) {
        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        openIntent.setDataAndType(fileUri, mime);
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(openIntent);
            Toast.makeText(this, "Opening " + fileName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "No app found to open file", e);
            Toast.makeText(this,
                    "No app found to open this file. Check Downloads folder.",
                    Toast.LENGTH_LONG).show();
            openDownloadsFolder();
        }
    }

    private void openDownloadsFolder() {
        try {
            Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "File saved to Downloads folder", Toast.LENGTH_LONG).show();
        }
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    private void deleteNote(Note note) {
        // FIX: null-safe UID check
        if (auth.getCurrentUser() == null) return;
        String currentUid = auth.getCurrentUser().getUid();

        if (note.getUploaderId() == null || !note.getUploaderId().equals(currentUid)) {
            Toast.makeText(this, "Only uploader can delete", Toast.LENGTH_SHORT).show();
            return;
        }

        notesRef.child(note.getId()).removeValue()
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this, "Note deleted successfully",
                                Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to delete: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String getExtensionFromFileType(String fileType) {
        if (fileType == null) return "";
        switch (fileType.toLowerCase()) {
            case "pdf":                    return ".pdf";
            case "image": case "jpg": case "jpeg": return ".jpg";
            case "png":                    return ".png";
            case "document": case "docx": return ".docx";
            case "doc":                    return ".doc";
            case "text": case "txt":       return ".txt";
            default:                       return "";
        }
    }

    private String getMimeType(String fileType) {
        if (fileType == null) return "*/*";
        switch (fileType.toLowerCase()) {
            case "pdf":                    return "application/pdf";
            case "image": case "jpg":
            case "jpeg": case "png":       return "image/*";
            case "document": case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc":                    return "application/msword";
            case "text": case "txt":       return "text/plain";
            default:                       return "*/*";
        }
    }

    private String getFileName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) return cursor.getString(nameIndex);
            }
        } catch (Exception e) {
            Log.e(TAG, "File name error", e);
        }
        return null;
    }

    private String getFileNameWithoutExtension(String fileName) {
        if (fileName == null) return "file";
        fileName = fileName.trim();
        int lastDot = fileName.lastIndexOf('.');
        return (lastDot > 0) ? fileName.substring(0, lastDot).trim() : fileName;
    }

    private long getFileSize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1) return cursor.getLong(sizeIndex);
            }
        } catch (Exception e) {
            Log.e(TAG, "File size error", e);
        }
        return -1;
    }

    private String detectFileType(Uri uri, String fileName) {
        String mimeType = getContentResolver().getType(uri);
        if (mimeType != null) {
            if (mimeType.contains("pdf"))                             return "pdf";
            else if (mimeType.contains("text"))                       return "text";
            else if (mimeType.contains("word") || mimeType.contains("document")) return "document";
            else if (mimeType.contains("image"))                      return "image";
        }
        if (fileName != null) {
            int lastDot = fileName.lastIndexOf('.');
            if (lastDot > 0) {
                switch (fileName.substring(lastDot + 1).toLowerCase()) {
                    case "pdf":                           return "pdf";
                    case "txt": case "text":              return "text";
                    case "doc": case "docx":              return "document";
                    case "jpg": case "jpeg":
                    case "png": case "gif":               return "image";
                }
            }
        }
        return "other";
    }
}