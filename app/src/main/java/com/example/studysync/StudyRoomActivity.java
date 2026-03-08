package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StudyRoomActivity extends AppCompatActivity {

    private static final String TAG = "StudyRoomActivity";

    // ── Views ──────────────────────────────────────────────────────────────────
    private TextInputEditText etRoomCode, etRoomName;
    private Button            btnJoinRoom, btnCreateRoom;
    private CardView          cardCreateRoom;
    private ProgressBar       progressBar;
    private LinearLayout      tabCreate, tabJoin;
    private LinearLayout      panelCreate, panelJoin;
    private LinearLayout      skeletonView;
    private LinearLayout      btnPaste;
    private LinearLayout      recentCodesSection, recentCodesContainer;
    private TextView          tvCharCounter;

    // ── State ──────────────────────────────────────────────────────────────────
    private boolean isCreateMode = true;
    private FirebaseAuth auth;

    // ── Skeleton pulse animator ────────────────────────────────────────────────
    private ValueAnimator skeletonAnimator;

    // ──────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_room);

        auth = FirebaseAuth.getInstance();

        initViews();
        animateEntrance();
        loadRecentRooms();

        // Pre-select tab from dashboard intent
        String mode = getIntent().getStringExtra("mode");
        if ("join".equals(mode)) {
            switchTab(false);
        } else {
            switchTab(true);
        }
    }

    // ── View wiring ────────────────────────────────────────────────────────────
    private void initViews() {
        etRoomCode           = findViewById(R.id.etRoomCode);
        etRoomName           = findViewById(R.id.etRoomName);
        btnJoinRoom          = findViewById(R.id.btnJoinRoom);
        btnCreateRoom        = findViewById(R.id.btnCreateRoom);
        cardCreateRoom       = findViewById(R.id.cardCreateRoom);
        progressBar          = findViewById(R.id.progressBar);
        tabCreate            = findViewById(R.id.tabCreate);
        tabJoin              = findViewById(R.id.tabJoin);
        panelCreate          = findViewById(R.id.panelCreate);
        panelJoin            = findViewById(R.id.panelJoin);
        skeletonView         = findViewById(R.id.skeletonView);
        btnPaste             = findViewById(R.id.btnPaste);
        recentCodesSection   = findViewById(R.id.recentCodesSection);
        recentCodesContainer = findViewById(R.id.recentCodesContainer);
        tvCharCounter        = findViewById(R.id.tvCharCounter);

        // Tabs
        tabCreate.setOnClickListener(v -> switchTab(true));
        tabJoin.setOnClickListener(v   -> switchTab(false));

        // Create
        cardCreateRoom.setOnClickListener(v -> {
            animateButton(cardCreateRoom);
            hapticFeedback();
            cardCreateRoom.postDelayed(this::createRoom, 200);
        });
        btnCreateRoom.setOnClickListener(v -> {
            animateButton(btnCreateRoom);
            hapticFeedback();
            btnCreateRoom.postDelayed(this::createRoom, 200);
        });

        // Join
        btnJoinRoom.setOnClickListener(v -> {
            animateButton(btnJoinRoom);
            hapticFeedback();
            btnJoinRoom.postDelayed(this::joinRoom, 200);
        });

        // ── 📋 Paste button ───────────────────────────────────────────────────
        btnPaste.setOnClickListener(v -> {
            animateButton(btnPaste);
            hapticFeedback();
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                if (item != null && item.getText() != null) {
                    String pasted = item.getText().toString()
                            .trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
                    if (pasted.length() > 6) pasted = pasted.substring(0, 6);
                    etRoomCode.setText(pasted);
                    etRoomCode.setSelection(pasted.length());
                    Toast.makeText(this, "Code pasted!", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Nothing in clipboard", Toast.LENGTH_SHORT).show();
            }
        });

        // ── Live character counter + auto-submit ──────────────────────────────
        etRoomCode.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int len = s.length();
                tvCharCounter.setText(len + " / 6");

                // Counter turns green when full
                tvCharCounter.setTextColor(len == 6 ? 0xFF4CAF50 : 0xFF3D5060);

                // Auto-submit when 6th char typed
                if (len == 6) {
                    // Small delay so user sees the counter turn green before joining
                    etRoomCode.postDelayed(() -> {
                        hapticFeedback();
                        joinRoom();
                    }, 400);
                }
            }
        });

        // IME "Done" on code field also triggers join
        etRoomCode.setOnEditorActionListener((v, actionId, event) -> {
            joinRoom();
            return true;
        });

        // IME "Done" on room name field triggers create
        if (etRoomName != null) {
            etRoomName.setOnEditorActionListener((v, actionId, event) -> {
                createRoom();
                return true;
            });
        }
    }

    // ── Tab switching ──────────────────────────────────────────────────────────
    private void switchTab(boolean createMode) {
        isCreateMode = createMode;

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
            if (child instanceof TextView) ((TextView) child).setTextColor(color);
        }
    }

    private void crossFade(View hide, View show) {
        hide.animate().alpha(0f).setDuration(180)
                .withEndAction(() -> hide.setVisibility(View.GONE)).start();
        show.setAlpha(0f);
        show.setVisibility(View.VISIBLE);
        show.setTranslationY(20f);
        show.animate().alpha(1f).translationY(0f)
                .setDuration(250)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    // ── Create Room ────────────────────────────────────────────────────────────
    private void createRoom() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "You must be logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Read optional room name
        String roomName = "";
        if (etRoomName != null && etRoomName.getText() != null) {
            roomName = etRoomName.getText().toString().trim();
        }
        final String finalRoomName = roomName;

        showSkeleton(true);
        String roomCode = generateRandomCode();

        DatabaseReference roomsRef = FirebaseDatabase.getInstance().getReference("Rooms");
        roomsRef.child(roomCode).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                showSkeleton(false);
                handleError("Failed to check availability. Try again.");
                return;
            }
            DataSnapshot snap = task.getResult();
            if (snap != null && snap.exists()) {
                showSkeleton(false);
                createRoom(); // collision — regenerate
            } else {
                createRoomInDatabase(roomCode, user.getUid(), finalRoomName);
            }
        });
    }

    private void createRoomInDatabase(String roomCode, String creatorUid, String customName) {
        DatabaseReference roomRef = FirebaseDatabase.getInstance()
                .getReference("Rooms").child(roomCode);

        // Use custom name if provided, else fall back to room code
        String displayName = (!customName.isEmpty()) ? customName : roomCode;

        Map<String, Object> roomData = new HashMap<>();
        roomData.put("roomCode",  roomCode);
        roomData.put("roomName",  displayName);
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
                    showSkeleton(false);

                    // ── Auto-copy code to clipboard ───────────────────────────
                    copyToClipboard(roomCode);
                    Toast.makeText(this,
                            "✅ Room created! Code copied — share it with friends 🎉",
                            Toast.LENGTH_LONG).show();

                    navigateToRoom(roomCode);
                })
                .addOnFailureListener(e -> {
                    showSkeleton(false);
                    handleError("Error creating room: " + e.getMessage());
                });
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

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "You must be logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        showSkeleton(true);
        DatabaseReference roomRef = FirebaseDatabase.getInstance()
                .getReference("Rooms").child(roomCode);

        final String finalCode = roomCode;
        roomRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                showSkeleton(false);
                handleError("Failed to check room. Try again.");
                return;
            }
            DataSnapshot snap = task.getResult();
            if (snap != null && snap.exists()) {
                roomRef.child("members").child(user.getUid()).setValue(true)
                        .addOnSuccessListener(aVoid -> {
                            showSkeleton(false);
                            Toast.makeText(this,
                                    "✅ Joined room " + finalCode + "!",
                                    Toast.LENGTH_SHORT).show();
                            navigateToRoom(finalCode);
                        })
                        .addOnFailureListener(e -> {
                            showSkeleton(false);
                            handleError("Failed to join: " + e.getMessage());
                        });
            } else {
                showSkeleton(false);
                etRoomCode.setError("This room doesn't exist.");
                shakeView(etRoomCode);
                Toast.makeText(this, "Room not found — check the code.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Recent rooms chips ─────────────────────────────────────────────────────
    private void loadRecentRooms() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        FirebaseDatabase.getInstance().getReference("Rooms")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<String[]> rooms = new ArrayList<>(); // [code, name]
                        for (DataSnapshot room : snapshot.getChildren()) {
                            if (room.child("members").hasChild(user.getUid())) {
                                String code = room.getKey();
                                String name = room.child("roomName").getValue(String.class);
                                if (name == null || name.isEmpty()) name = code;
                                rooms.add(new String[]{code, name});
                            }
                        }

                        if (rooms.isEmpty()) return;

                        recentCodesSection.setVisibility(View.VISIBLE);
                        recentCodesContainer.removeAllViews();

                        // Show last 3 most recently joined
                        int limit = Math.min(rooms.size(), 3);
                        for (int i = rooms.size() - 1; i >= rooms.size() - limit; i--) {
                            addRecentChip(rooms.get(i)[0], rooms.get(i)[1]);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    private void addRecentChip(String code, String name) {
        FrameLayout chip = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd((int) (10 * getResources().getDisplayMetrics().density));
        chip.setLayoutParams(lp);
        chip.setBackground(getDrawable(R.drawable.recent_room_chip_bg));
        chip.setClickable(true);
        chip.setFocusable(true);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        int ph = (int) (12 * getResources().getDisplayMetrics().density);
        int pv = (int) (10 * getResources().getDisplayMetrics().density);
        inner.setPadding(ph, pv, ph, pv);
        inner.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextColor(0xFFFFFFFF);
        tvName.setTextSize(13f);
        tvName.setTypeface(tvName.getTypeface(), android.graphics.Typeface.BOLD);
        tvName.setMaxLines(1);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvName.setMaxWidth((int) (130 * getResources().getDisplayMetrics().density));

        TextView tvCode = new TextView(this);
        tvCode.setText("# " + code);
        tvCode.setTextColor(0xFF4CAF50);
        tvCode.setTextSize(10f);
        LinearLayout.LayoutParams codeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        codeLp.topMargin = (int) (2 * getResources().getDisplayMetrics().density);
        tvCode.setLayoutParams(codeLp);

        inner.addView(tvName);
        inner.addView(tvCode);
        chip.addView(inner);

        // Tap chip → fill the input field with this code
        chip.setOnClickListener(v -> {
            animateButton(chip);
            hapticFeedback();
            etRoomCode.setText(code);
            etRoomCode.setSelection(code.length());
        });

        // Fade in
        chip.setAlpha(0f);
        chip.animate().alpha(1f).setDuration(250)
                .setStartDelay(60L * recentCodesContainer.getChildCount()).start();

        recentCodesContainer.addView(chip);
    }

    // ── Clipboard ──────────────────────────────────────────────────────────────
    private void copyToClipboard(String text) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("Room Code", text);
            clipboard.setPrimaryClip(clip);
        }
    }

    // ── Skeleton loader ────────────────────────────────────────────────────────
    private void showSkeleton(boolean show) {
        if (show) {
            // Hide active panel, show skeleton
            if (isCreateMode) panelCreate.setVisibility(View.GONE);
            else              panelJoin.setVisibility(View.GONE);

            skeletonView.setVisibility(View.VISIBLE);
            startSkeletonPulse();
        } else {
            stopSkeletonPulse();
            skeletonView.setVisibility(View.GONE);

            if (isCreateMode) panelCreate.setVisibility(View.VISIBLE);
            else              panelJoin.setVisibility(View.VISIBLE);
        }

        // Disable buttons during load
        if (btnJoinRoom   != null) btnJoinRoom.setEnabled(!show);
        if (btnCreateRoom != null) btnCreateRoom.setEnabled(!show);
        if (cardCreateRoom != null) cardCreateRoom.setEnabled(!show);
    }

    private void startSkeletonPulse() {
        skeletonAnimator = ValueAnimator.ofFloat(0.4f, 0.9f);
        skeletonAnimator.setDuration(900);
        skeletonAnimator.setRepeatMode(ValueAnimator.REVERSE);
        skeletonAnimator.setRepeatCount(ValueAnimator.INFINITE);
        skeletonAnimator.addUpdateListener(anim -> {
            float alpha = (float) anim.getAnimatedValue();
            for (int i = 0; i < skeletonView.getChildCount(); i++) {
                skeletonView.getChildAt(i).setAlpha(alpha);
            }
        });
        skeletonAnimator.start();
    }

    private void stopSkeletonPulse() {
        if (skeletonAnimator != null) {
            skeletonAnimator.cancel();
            skeletonAnimator = null;
        }
    }

    // ── Navigation ─────────────────────────────────────────────────────────────
    private void navigateToRoom(String roomCode) {
        Intent intent = new Intent(this, StudyRoomInsideActivity.class);
        intent.putExtra("roomCode", roomCode);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void handleError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.e(TAG, message);
    }

    // ── Haptic feedback ────────────────────────────────────────────────────────
    private void hapticFeedback() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(
                            30, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(30);
                }
            }
        } catch (Exception ignored) {}
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

    // ── Animations ─────────────────────────────────────────────────────────────
    private void animateEntrance() {
        int[] ids = {R.id.tabCreate, R.id.tabJoin, R.id.panelCreate, R.id.panelJoin};
        for (int i = 0; i < ids.length; i++) {
            View v = findViewById(ids[i]);
            if (v == null) continue;
            v.setAlpha(0f);
            v.setTranslationY(40f);
            v.animate().alpha(1f).translationY(0f)
                    .setDuration(400).setStartDelay(80L * i)
                    .setInterpolator(new AccelerateDecelerateInterpolator()).start();
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

    private void shakeView(View v) {
        ObjectAnimator shake = ObjectAnimator.ofFloat(v, "translationX",
                0f, -14f, 14f, -10f, 10f, -6f, 6f, 0f);
        shake.setDuration(400);
        shake.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSkeletonPulse();
    }
}