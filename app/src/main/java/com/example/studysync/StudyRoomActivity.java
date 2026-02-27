package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public class StudyRoomActivity extends AppCompatActivity {

    private static final String TAG = "StudyRoomActivity";

    private TextInputEditText etRoomCode;
    private Button btnJoinRoom;
    private CardView cardCreateRoom;
    private ProgressBar progressBar;

    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_room);

        // Initialize views
        etRoomCode = findViewById(R.id.etRoomCode);
        btnJoinRoom = findViewById(R.id.btnJoinRoom);
        cardCreateRoom = findViewById(R.id.cardCreateRoom);
        progressBar = findViewById(R.id.progressBar);

        auth = FirebaseAuth.getInstance();

        // Entrance animation
        animateEntrance();

        // Button click listeners
        btnJoinRoom.setOnClickListener(v -> {
            animateButton(btnJoinRoom);
            btnJoinRoom.postDelayed(this::joinRoom, 200);
        });

        cardCreateRoom.setOnClickListener(v -> {
            animateButton(cardCreateRoom);
            cardCreateRoom.postDelayed(this::createRoom, 200);
        });
    }

    private void animateEntrance() {
        View contentView = findViewById(android.R.id.content);
        if (contentView != null) {
            contentView.setAlpha(0f);
            contentView.animate()
                    .alpha(1f)
                    .setDuration(400)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
    }

    private void animateButton(View view) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f, 1f);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(scaleX, scaleY);
        animatorSet.setDuration(200);
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.start();
    }

    private void createRoom() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to create a room.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Create room button clicked");
        setInProgress(true);
        String roomCode = generateRandomCode();
        Log.d(TAG, "Generated room code: " + roomCode);

        DatabaseReference roomsRef = FirebaseDatabase.getInstance().getReference("Rooms");

        // Check if room code already exists
        roomsRef.child(roomCode).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.e(TAG, "Failed to check room availability", task.getException());
                handleError("Failed to check room availability. Try again.");
                return;
            }

            DataSnapshot snap = task.getResult();
            if (snap != null && snap.exists()) {
                Log.d(TAG, "Room code collision, generating new code");
                setInProgress(false);
                createRoom(); // Recursive call
            } else {
                Log.d(TAG, "Room code available, creating room");
                createRoomInDatabase(roomCode, currentUser.getUid());
            }
        });
    }

    private void createRoomInDatabase(String roomCode, String creatorUid) {
        Log.d(TAG, "Creating room in database: " + roomCode);

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
                    Log.d(TAG, "✅ Room created successfully: " + roomCode);
                    Toast.makeText(this, "Room created: " + roomCode, Toast.LENGTH_LONG).show();
                    navigateToRoom(roomCode);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error creating room", e);
                    handleError("Error creating room: " + e.getMessage());
                });
    }

    private void joinRoom() {
        String roomCode = etRoomCode.getText() != null ?
                etRoomCode.getText().toString().trim().toUpperCase() : "";

        Log.d(TAG, "Join room button clicked. Room code: " + roomCode);

        if (TextUtils.isEmpty(roomCode)) {
            etRoomCode.setError("Room code cannot be empty.");
            etRoomCode.requestFocus();
            Toast.makeText(this, "Please enter a room code", Toast.LENGTH_SHORT).show();
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

        Log.d(TAG, "Checking if room exists: " + roomCode);

        roomRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.e(TAG, "Failed to check room existence", task.getException());
                handleError("Failed to check room existence. Try again.");
                return;
            }

            DataSnapshot snap = task.getResult();
            if (snap != null && snap.exists()) {
                Log.d(TAG, "✅ Room exists, joining: " + roomCode);
                roomRef.child("members").child(currentUser.getUid()).setValue(true)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "✅ Successfully joined room");
                            Toast.makeText(this, "Joined room: " + roomCode, Toast.LENGTH_SHORT).show();
                            navigateToRoom(roomCode);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "❌ Failed to join room", e);
                            handleError("Failed to join room: " + e.getMessage());
                        });
            } else {
                Log.e(TAG, "❌ Room does not exist: " + roomCode);
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
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        startActivity(intent);

        Log.d(TAG, "Started StudyRoomInsideActivity");
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
        if (cardCreateRoom != null) {
            cardCreateRoom.setEnabled(!inProgress);
        }
    }
}