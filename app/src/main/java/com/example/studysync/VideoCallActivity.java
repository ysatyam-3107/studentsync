package com.example.studysync;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.TextureView;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.*;
import java.util.Locale;

import io.agora.rtc2.*;
import io.agora.rtc2.video.VideoCanvas;
import io.agora.rtc2.ChannelMediaOptions;

public class VideoCallActivity extends AppCompatActivity {

    private static final int PERMISSION_REQ_ID   = 22;
    private static final int CONTROL_PANEL_DP    = 140; // approx height of button bar

    private GridLayout   remoteContainer;
    private FrameLayout  localContainer;
    private TextView     tvCount, tvCallDuration;
    private ImageButton  btnMute, btnVideo, btnEnd, btnSwitch, btnMuteAll;

    private RtcEngine rtcEngine;
    private boolean muted        = false;
    private boolean videoEnabled = true;
    private boolean allMuted     = false;

    private final List<Integer>      remoteUids = new ArrayList<>();
    private final Map<Integer, View> videoTiles = new HashMap<>();

    // ── Timer ──────────────────────────────────
    private final Handler  timerHandler   = new Handler();
    private int            secondsElapsed = 0;
    private final Runnable timerRunnable  = new Runnable() {
        @Override public void run() {
            secondsElapsed++;
            int min = secondsElapsed / 60;
            int sec = secondsElapsed % 60;
            if (tvCallDuration != null)
                tvCallDuration.setText(
                        String.format(Locale.getDefault(), "%02d:%02d", min, sec));
            timerHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        initUI();
        startCallTimer();

        if (hasPermissions()) initAgora();
        else ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQ_ID);
    }

    private void initUI() {
        remoteContainer = findViewById(R.id.remoteVideoContainer);
        localContainer  = (FrameLayout) ((androidx.cardview.widget.CardView)
                findViewById(R.id.localVideoContainer)).getChildAt(0);

        tvCount        = findViewById(R.id.tvParticipantCount);
        tvCallDuration = findViewById(R.id.tvCallDuration);

        btnMute    = findViewById(R.id.btnMute);
        btnVideo   = findViewById(R.id.btnVideo);
        btnEnd     = findViewById(R.id.btnEndCall);
        btnSwitch  = findViewById(R.id.btnSwitchCamera);
        btnMuteAll = findViewById(R.id.btnMuteAll);

        btnMute.setOnClickListener(v    -> toggleMute());
        btnVideo.setOnClickListener(v   -> toggleVideo());
        btnSwitch.setOnClickListener(v  -> switchCamera());
        btnEnd.setOnClickListener(v     -> endCall());
        btnMuteAll.setOnClickListener(v -> toggleMuteAll());

        updateCount();
    }

    // ── Timer ──────────────────────────────────
    private void startCallTimer() {
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    // ── Agora ──────────────────────────────────
    private void initAgora() {
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext      = getApplicationContext();
            config.mAppId        = AgoraConfig.APP_ID;
            config.mEventHandler = rtcHandler;

            rtcEngine = RtcEngine.create(config);
            rtcEngine.enableVideo();
            setupLocalVideo();
            joinChannel();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupLocalVideo() {
        TextureView view = new TextureView(this);
        localContainer.addView(view);
        rtcEngine.setupLocalVideo(
                new VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, 0));
    }

    private void joinChannel() {
        String roomCode = getIntent().getStringExtra("roomCode");
        if (roomCode == null || roomCode.isEmpty()) roomCode = "studysync_default";

        ChannelMediaOptions options = new ChannelMediaOptions();
        options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
        rtcEngine.joinChannel(AgoraConfig.TOKEN, roomCode, 0, options);
    }

    // ── RTC Events ─────────────────────────────
    private final IRtcEngineEventHandler rtcHandler = new IRtcEngineEventHandler() {
        @Override public void onUserJoined(int uid, int elapsed) {
            runOnUiThread(() -> addRemoteUser(uid));
        }
        @Override public void onUserOffline(int uid, int reason) {
            runOnUiThread(() -> removeRemoteUser(uid));
        }
        @Override public void onActiveSpeaker(int uid) {
            runOnUiThread(() -> highlightSpeaker(uid));
        }
    };

    // ── Add / Remove ───────────────────────────
    private void addRemoteUser(int uid) {
        if (remoteUids.contains(uid)) return;
        remoteUids.add(uid);

        View tile = getLayoutInflater().inflate(R.layout.video_tile, null);
        FrameLayout surface = tile.findViewById(R.id.videoSurface);

        TextureView view = new TextureView(this);
        surface.addView(view);

        TextView name = tile.findViewById(R.id.tvName);
        name.setText("User " + uid);

        videoTiles.put(uid, tile);
        remoteContainer.addView(tile);
        rtcEngine.setupRemoteVideo(
                new VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, uid));

        tile.setAlpha(0f);
        tile.setScaleX(0.85f);
        tile.setScaleY(0.85f);
        tile.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).start();

        resizeGrid();
        updateCount();
    }

    private void removeRemoteUser(int uid) {
        remoteUids.remove(Integer.valueOf(uid));
        View tile = videoTiles.get(uid);
        if (tile != null) {
            tile.animate().alpha(0f).setDuration(200)
                    .withEndAction(() -> remoteContainer.removeView(tile));
            videoTiles.remove(uid);
        }
        resizeGrid();
        updateCount();
    }

    // ── Grid Resize ────────────────────────────
    /**
     * Calculates tile size based on number of users.
     * - ≤4 users  → fits on screen perfectly, no scroll needed
     * - 5-9 users → still fits but tiles shrink
     * - 10+ users → fixed tile height, ScrollView activates automatically
     *
     * Tile height SUBTRACTS the control panel so nothing hides behind buttons.
     */
    private void resizeGrid() {
        int total = remoteUids.size();
        if (total == 0) return;

        int cols = (total == 1) ? 1 : (int) Math.ceil(Math.sqrt(total));
        cols = Math.min(cols, 4); // hard cap at 4 columns
        int rows = (int) Math.ceil((double) total / cols);

        remoteContainer.setColumnCount(cols);
        remoteContainer.setRowCount(rows);

        float density        = getResources().getDisplayMetrics().density;
        int   screenWidth    = getResources().getDisplayMetrics().widthPixels;
        int   screenHeight   = getResources().getDisplayMetrics().heightPixels;
        int   controlPanelPx = (int) (CONTROL_PANEL_DP * density);
        int   availableH     = screenHeight - controlPanelPx;

        int tileW = screenWidth / cols;
        // For ≤3 rows: divide available height so all fit without scroll
        // For 4+ rows: fixed height so scroll kicks in (3 rows visible at once)
        int tileH = (rows <= 3) ? availableH / rows : availableH / 3;

        for (int i = 0; i < remoteContainer.getChildCount(); i++) {
            View child = remoteContainer.getChildAt(i);
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width  = tileW;
            p.height = tileH;
            child.setLayoutParams(p);
        }
    }

    private void updateCount() {
        if (tvCount != null)
            tvCount.setText("👥 " + (remoteUids.size() + 1) + " in call");
    }

    // ── Speaker highlight ──────────────────────
    private void highlightSpeaker(int uid) {
        for (Map.Entry<Integer, View> entry : videoTiles.entrySet()) {
            View tile = entry.getValue();
            if (entry.getKey() == uid) {
                tile.setBackgroundColor(0x3300E676);
                animateSpeaker(tile);
            } else {
                tile.setBackgroundColor(0x00000000);
            }
        }
    }

    private void animateSpeaker(View tile) {
        tile.animate().scaleX(1.04f).scaleY(1.04f).setDuration(150)
                .withEndAction(() ->
                        tile.animate().scaleX(1f).scaleY(1f).setDuration(150).start())
                .start();
    }

    // ── Controls ───────────────────────────────
    private void toggleMute() {
        muted = !muted;
        rtcEngine.muteLocalAudioStream(muted);
        btnMute.setBackgroundResource(
                muted ? R.drawable.btn_control_muted : R.drawable.btn_control_normal);
        btnMute.setImageResource(
                muted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
        animateButton(btnMute);
    }

    private void toggleVideo() {
        videoEnabled = !videoEnabled;
        rtcEngine.muteLocalVideoStream(!videoEnabled);
        ((View) localContainer.getParent()).setVisibility(
                videoEnabled ? View.VISIBLE : View.INVISIBLE);
        btnVideo.setBackgroundResource(
                videoEnabled ? R.drawable.btn_control_normal : R.drawable.btn_control_muted);
        btnVideo.setImageResource(
                videoEnabled ? R.drawable.ic_video : R.drawable.ic_video_off);
        animateButton(btnVideo);
    }

    private void switchCamera() {
        rtcEngine.switchCamera();
        animateButton(btnSwitch);
        ((View) localContainer.getParent()).animate()
                .rotationY(90f).setDuration(150)
                .withEndAction(() ->
                        ((View) localContainer.getParent()).animate()
                                .rotationY(0f).setDuration(150).start())
                .start();
    }

    private void toggleMuteAll() {
        allMuted = !allMuted;
        for (Integer uid : remoteUids)
            rtcEngine.muteRemoteAudioStream(uid, allMuted);

        // ✅ Fixed: removed reference to btn_control_secondary (doesn't exist)
        //    Uses btn_control_muted (red) when active, btn_control_normal otherwise
        btnMuteAll.setBackgroundResource(
                allMuted ? R.drawable.btn_control_muted : R.drawable.btn_control_normal);
        animateButton(btnMuteAll);
        Toast.makeText(this,
                allMuted ? "All participants muted" : "All participants unmuted",
                Toast.LENGTH_SHORT).show();
    }

    private void endCall() {
        btnEnd.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100)
                .withEndAction(this::finish).start();
    }

    private void animateButton(View btn) {
        btn.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
                .withEndAction(() ->
                        btn.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                .start();
    }

    // ── Permissions ────────────────────────────
    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_ID && hasPermissions()) initAgora();
        else {
            Toast.makeText(this,
                    "Camera & microphone permission required",
                    Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // ── Lifecycle ──────────────────────────────
    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        if (rtcEngine != null) {
            rtcEngine.leaveChannel();
            RtcEngine.destroy();
            rtcEngine = null;
        }
    }
}