package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public class StudyRoomActivity extends AppCompatActivity {

    private static final String TAG = "StudyRoomActivity";

    private EditText etRoomCode;
    private Button btnJoinRoom, btnCreateRoom;
    private ProgressBar progressBar;

    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_room);

        etRoomCode = findViewById(R.id.etRoomCode);
        btnJoinRoom = findViewById(R.id.btnJoinRoom);
        btnCreateRoom = findViewById(R.id.btnCreateRoom);
        progressBar = findViewById(R.id.progressBar);

        auth = FirebaseAuth.getInstance();

        btnJoinRoom.setOnClickListener(v -> joinRoom());
        btnCreateRoom.setOnClickListener(v -> createRoom());
    }

    private void createRoom() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to create a room.", Toast.LENGTH_SHORT).show();
            return;
        }

        setInProgress(true);
        String roomCode = generateRandomCode();

        DatabaseReference roomsRef = FirebaseDatabase.getInstance().getReference("Rooms");

        // Check if room code already exists
        roomsRef.child(roomCode).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                handleError("Failed to check room availability. Try again.");
                return;
            }

            DataSnapshot snap = task.getResult();
            if (snap != null && snap.exists()) {
                // Collision - retry with new code
                createRoom(); // Recursive call
            } else {
                // Create the room
                createRoomInDatabase(roomCode, currentUser.getUid());
            }
        });
    }

    private void createRoomInDatabase(String roomCode, String creatorUid) {
        DatabaseReference roomRef = FirebaseDatabase.getInstance()
                .getReference("Rooms").child(roomCode);

        Map<String, Object> roomData = new HashMap<>();
        roomData.put("roomCode", roomCode);
        roomData.put("createdBy", creatorUid);
        roomData.put("createdAt", ServerValue.TIMESTAMP);

        // Add creator as first member
        Map<String, Object> members = new HashMap<>();
        members.put(creatorUid, true);
        roomData.put("members", members);

        // Initialize timer
        Map<String, Object> timer = new HashMap<>();
        timer.put("running", false);
        timer.put("endTime", 0);
        timer.put("isBreak", false);
        roomData.put("timer", timer);

        roomRef.setValue(roomData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Room created successfully: " + roomCode);
                    Toast.makeText(this, "Room created: " + roomCode, Toast.LENGTH_LONG).show();
                    navigateToRoom(roomCode);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating room", e);
                    handleError("Error creating room: " + e.getMessage());
                });
    }

    private void joinRoom() {
        String roomCode = etRoomCode.getText() != null ?
                etRoomCode.getText().toString().trim().toUpperCase() : "";

        if (TextUtils.isEmpty(roomCode)) {
            etRoomCode.setError("Room code cannot be empty.");
            etRoomCode.requestFocus();
            return;
        }

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to join a room.", Toast.LENGTH_SHORT).show();
            return;
        }

        setInProgress(true);
        DatabaseReference roomRef = FirebaseDatabase.getInstance()
                .getReference("Rooms").child(roomCode);

        roomRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                handleError("Failed to check room existence. Try again.");
                return;
            }

            DataSnapshot snap = task.getResult();
            if (snap != null && snap.exists()) {
                // Room exists - add user as member
                roomRef.child("members").child(currentUser.getUid()).setValue(true)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, "Joined room: " + roomCode, Toast.LENGTH_SHORT).show();
                            navigateToRoom(roomCode);
                        })
                        .addOnFailureListener(e ->
                                handleError("Failed to join room: " + e.getMessage()));
            } else {
                setInProgress(false);
                etRoomCode.setError("This room does not exist.");
                Toast.makeText(this, "Invalid Room Code.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String generateRandomCode() {
        final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        final int LENGTH = 6;
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private void navigateToRoom(String roomCode) {
        setInProgress(false);

        Log.d(TAG, "Navigating to room: " + roomCode);

        Intent intent = new Intent(StudyRoomActivity.this, StudyRoomInsideActivity.class);
        intent.putExtra("roomCode", roomCode);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); // Clear any previous activities

        startActivity(intent);

        Log.d(TAG, "Started StudyRoomInsideActivity");

        // Don't call finish() here - let user go back to StudyRoomActivity
    }
    private void handleError(String message) {
        setInProgress(false);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.e(TAG, message);
    }

    private void setInProgress(boolean inProgress) {
        if (progressBar != null) {
            progressBar.setVisibility(inProgress ? View.VISIBLE : View.GONE);
        }
        btnJoinRoom.setEnabled(!inProgress);
        btnCreateRoom.setEnabled(!inProgress);
    }
}