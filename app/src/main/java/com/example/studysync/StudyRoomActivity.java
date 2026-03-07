package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
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

    // ── Views ──────────────────────────────────────────────────────────────────
    private TextInputEditText etRoomCode;
    private Button            btnJoinRoom, btnCreateRoom;
    private CardView          cardCreateRoom;
    private ProgressBar       progressBar;
    private LinearLayout      tabCreate, tabJoin;
    private LinearLayout      panelCreate, panelJoin;


    // ── State ──────────────────────────────────────────────────────────────────
    private boolean isCreateMode = true;

    private FirebaseAuth auth;

    // ──────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_room);

        auth = FirebaseAuth.getInstance();

        initViews();
        animateEntrance();

        // If launched from dashboard with a mode flag, pre-select that tab
        String mode = getIntent().getStringExtra("mode");
        if ("join".equals(mode)) {
            switchTab(false); // start on Join tab
        } else {
            switchTab(true);  // default: Create tab
        }
    }

    // ── View wiring ────────────────────────────────────────────────────────────
    private void initViews() {
        etRoomCode    = findViewById(R.id.etRoomCode);
        btnJoinRoom   = findViewById(R.id.btnJoinRoom);
        btnCreateRoom = findViewById(R.id.btnCreateRoom);
        cardCreateRoom = findViewById(R.id.cardCreateRoom);
        progressBar   = findViewById(R.id.progressBar);
        tabCreate     = findViewById(R.id.tabCreate);
        tabJoin       = findViewById(R.id.tabJoin);
        panelCreate   = findViewById(R.id.panelCreate);
        panelJoin     = findViewById(R.id.panelJoin);

        // Tab switching
        tabCreate.setOnClickListener(v -> switchTab(true));
        tabJoin.setOnClickListener(v   -> switchTab(false));

        // Create Room — tap the big card OR the button
        cardCreateRoom.setOnClickListener(v -> {
            animateButton(cardCreateRoom);
            cardCreateRoom.postDelayed(this::createRoom, 200);
        });
        btnCreateRoom.setOnClickListener(v -> {
            animateButton(btnCreateRoom);
            btnCreateRoom.postDelayed(this::createRoom, 200);
        });

        // Join Room
        btnJoinRoom.setOnClickListener(v -> {
            animateButton(btnJoinRoom);
            btnJoinRoom.postDelayed(this::joinRoom, 200);
        });
    }

    // ── Tab switching with smooth panel crossfade ──────────────────────────────
    private void switchTab(boolean createMode) {
        isCreateMode = createMode;

        // Active tab — white text, green tinted bg
        // Inactive tab — muted text, dark bg
        if (createMode) {
            tabCreate.setBackgroundResource(R.drawable.tab_active_bg);
            tabJoin.setBackgroundResource(R.drawable.tab_inactive_bg);
            setTabTextColor(tabCreate, 0xFFFFFFFF);
            setTabTextColor(tabJoin,   0xFF607080);
            crossFade(panelJoin, panelCreate);
        } else {
            tabJoin.setBackgroundResource(R.drawable.tab_active_bg);
            tabCreate.setBackgroundResource(R.drawable.tab_inactive_bg);
            setTabTextColor(tabJoin,   0xFFFFFFFF);
            setTabTextColor(tabCreate, 0xFF607080);
            crossFade(panelCreate, panelJoin);
        }
    }

    private void setTabTextColor(LinearLayout tab, int color) {
        for (int i = 0; i < tab.getChildCount(); i++) {
            View child = tab.getChildAt(i);
            if (child instanceof android.widget.TextView) {
                ((android.widget.TextView) child).setTextColor(color);
            }
        }
    }

    private void crossFade(View hide, View show) {
        hide.animate().alpha(0f).setDuration(180)
                .withEndAction(() -> hide.setVisibility(View.GONE))
                .start();

        show.setAlpha(0f);
        show.setVisibility(View.VISIBLE);
        show.setTranslationY(20f);
        show.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(250)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    // ── Create Room ────────────────────────────────────────────────────────────
    private void createRoom() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to create a room.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Creating room...");
        setInProgress(true);
        String roomCode = generateRandomCode();

        DatabaseReference roomsRef = FirebaseDatabase.getInstance().getReference("Rooms");
        roomsRef.child(roomCode).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                handleError("Failed to check room availability. Try again.");
                return;
            }
            DataSnapshot snap = task.getResult();
            if (snap != null && snap.exists()) {
                setInProgress(false);
                createRoom(); // code collision — regenerate
            } else {
                createRoomInDatabase(roomCode, currentUser.getUid());
            }
        });
    }

    private void createRoomInDatabase(String roomCode, String creatorUid) {
        DatabaseReference roomRef = FirebaseDatabase.getInstance()
                .getReference("Rooms").child(roomCode);

        // Save room name = roomCode for now (can be extended to let user name it)
        Map<String, Object> roomData = new HashMap<>();
        roomData.put("roomCode",  roomCode);
        roomData.put("roomName",  roomCode);      // used by Recent Rooms chips
        roomData.put("createdBy", creatorUid);
        roomData.put("createdAt", ServerValue.TIMESTAMP);

        Map<String, Object> members = new HashMap<>();
        members.put(creatorUid, true);
        roomData.put("members", members);

        Map<String, Object> timer = new HashMap<>();
        timer.put("running", false);
        timer.put("endTime", 0);
        timer.put("isBreak", false);
        roomData.put("timer", timer);

        roomRef.setValue(roomData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Room created: " + roomCode);
                    showSuccessToast("Room created! Code: " + roomCode);
                    navigateToRoom(roomCode);
                })
                .addOnFailureListener(e -> handleError("Error creating room: " + e.getMessage()));
    }

    // ── Join Room ──────────────────────────────────────────────────────────────
    private void joinRoom() {
        String roomCode = etRoomCode.getText() != null
                ? etRoomCode.getText().toString().trim().toUpperCase()
                : "";

        if (TextUtils.isEmpty(roomCode)) {
            etRoomCode.setError("Room code cannot be empty.");
            etRoomCode.requestFocus();
            shakeView(etRoomCode);
            return;
        }

        if (roomCode.length() != 6) {
            etRoomCode.setError("Code must be 6 characters.");
            etRoomCode.requestFocus();
            shakeView(etRoomCode);
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
                handleError("Failed to check room. Try again.");
                return;
            }
            DataSnapshot snap = task.getResult();
            if (snap != null && snap.exists()) {
                roomRef.child("members").child(currentUser.getUid()).setValue(true)
                        .addOnSuccessListener(aVoid -> {
                            showSuccessToast("Joined room " + roomCode + "!");
                            navigateToRoom(roomCode);
                        })
                        .addOnFailureListener(e -> handleError("Failed to join: " + e.getMessage()));
            } else {
                setInProgress(false);
                etRoomCode.setError("This room doesn't exist.");
                shakeView(etRoomCode);
                Toast.makeText(this, "Room not found — check the code.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private String generateRandomCode() {
        final String CHARS  = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        final int    LENGTH = 6;
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        return sb.toString();
    }

    private void navigateToRoom(String roomCode) {
        setInProgress(false);
        Intent intent = new Intent(this, StudyRoomInsideActivity.class);
        intent.putExtra("roomCode", roomCode);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void handleError(String message) {
        setInProgress(false);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.e(TAG, message);
    }

    private void setInProgress(boolean inProgress) {
        if (progressBar != null)
            progressBar.setVisibility(inProgress ? View.VISIBLE : View.GONE);
        if (btnJoinRoom   != null) btnJoinRoom.setEnabled(!inProgress);
        if (btnCreateRoom != null) btnCreateRoom.setEnabled(!inProgress);
        if (cardCreateRoom != null) cardCreateRoom.setEnabled(!inProgress);
    }

    private void showSuccessToast(String message) {
        Toast.makeText(this, "✅ " + message, Toast.LENGTH_SHORT).show();
    }

    // ── Animations ─────────────────────────────────────────────────────────────
    private void animateEntrance() {
        // Stagger header + tabs + panel
        int[] ids = {R.id.tabCreate, R.id.tabJoin,
                R.id.panelCreate, R.id.panelJoin};
        for (int i = 0; i < ids.length; i++) {
            View v = findViewById(ids[i]);
            if (v == null) continue;
            v.setAlpha(0f);
            v.setTranslationY(40f);
            v.animate()
                    .alpha(1f).translationY(0f)
                    .setDuration(400)
                    .setStartDelay(80L * i)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
    }

    private void animateButton(View view) {
        ObjectAnimator sx = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.94f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.94f, 1f);
        AnimatorSet set   = new AnimatorSet();
        set.playTogether(sx, sy);
        set.setDuration(200);
        set.setInterpolator(new OvershootInterpolator(2f));
        set.start();
    }

    // Horizontal shake — used for invalid input feedback
    private void shakeView(View v) {
        ObjectAnimator shake = ObjectAnimator.ofFloat(v, "translationX",
                0f, -14f, 14f, -10f, 10f, -6f, 6f, 0f);
        shake.setDuration(400);
        shake.start();
    }
}