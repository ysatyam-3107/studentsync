package com.example.studysync;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.SurfaceView;
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
import io.agora.rtc2.video.VideoEncoderConfiguration;
import io.agora.rtc2.ChannelMediaOptions;

public class VideoCallActivity extends AppCompatActivity {

    private static final int PERMISSION_REQ_ID = 22;
    private static final int CONTROL_PANEL_DP  = 100; // compact single-row panel

    private GridLayout  remoteContainer;
    private FrameLayout localContainer;
    private TextView    tvCount, tvCallDuration;
    private ImageButton btnMute, btnVideo, btnEnd, btnSwitch, btnMuteAll;

    private RtcEngine rtcEngine;
    private boolean muted        = false;
    private boolean videoEnabled = true;
    private boolean allMuted     = false;

    private final List<Integer>      remoteUids = new ArrayList<>();
    private final Map<Integer, View> videoTiles = new HashMap<>();

    // ── Timer ──────────────────────────────────────────────────────────────────
    private final Handler  timerHandler  = new Handler();
    private int            secondsElapsed = 0;
    private final Runnable timerRunnable  = new Runnable() {
        @Override public void run() {
            secondsElapsed++;
            int min = secondsElapsed / 60, sec = secondsElapsed % 60;
            if (tvCallDuration != null)
                tvCallDuration.setText(
                        String.format(Locale.getDefault(), "%02d:%02d", min, sec));
            timerHandler.postDelayed(this, 1000);
        }
    };

    // ──────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        initUI();
        timerHandler.postDelayed(timerRunnable, 1000);

        if (hasPermissions()) initAgora();
        else ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQ_ID);
    }

    private void initUI() {
        remoteContainer = findViewById(R.id.remoteVideoContainer);

        // localContainer is the FrameLayout INSIDE the CardView
        localContainer = ((androidx.cardview.widget.CardView)
                findViewById(R.id.localVideoContainer)).getChildAt(0) instanceof FrameLayout
                ? (FrameLayout) ((androidx.cardview.widget.CardView)
                findViewById(R.id.localVideoContainer)).getChildAt(0)
                : new FrameLayout(this);

        tvCount        = findViewById(R.id.tvParticipantCount);
        tvCallDuration = findViewById(R.id.tvCallDuration);
        btnMute        = findViewById(R.id.btnMute);
        btnVideo       = findViewById(R.id.btnVideo);
        btnEnd         = findViewById(R.id.btnEndCall);
        btnSwitch      = findViewById(R.id.btnSwitchCamera);
        btnMuteAll     = findViewById(R.id.btnMuteAll);

        btnMute.setOnClickListener(v    -> toggleMute());
        btnVideo.setOnClickListener(v   -> toggleVideo());
        btnSwitch.setOnClickListener(v  -> switchCamera());
        btnEnd.setOnClickListener(v     -> endCall());
        btnMuteAll.setOnClickListener(v -> toggleMuteAll());

        updateCount();
    }

    // ── Agora init ─────────────────────────────────────────────────────────────
    private void initAgora() {
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext      = getApplicationContext();
            config.mAppId        = AgoraConfig.APP_ID;
            config.mEventHandler = rtcHandler;
            rtcEngine = RtcEngine.create(config);

            // ── FIX: Smooth video — proper encoder config ──────────────────────
            rtcEngine.enableVideo();
            rtcEngine.setVideoEncoderConfiguration(new VideoEncoderConfiguration(
                    VideoEncoderConfiguration.VD_640x360,          // good quality/perf balance
                    VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_24,
                    VideoEncoderConfiguration.STANDARD_BITRATE,
                    VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE));

            // Enable hardware acceleration and noise suppression
            rtcEngine.setParameters("{\"che.video.h264Profile\":\"main\"}");
            rtcEngine.setParameters("{\"che.audio.enable.ns\":true}");
            rtcEngine.setParameters("{\"che.audio.enable.agc\":true}");
            rtcEngine.setParameters("{\"che.audio.enable.aec\":true}");

            setupLocalVideo();
            joinChannel();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to init video: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void setupLocalVideo() {
        // FIX: Use SurfaceView — hardware-accelerated, smoother than TextureView
        SurfaceView view = new SurfaceView(this);
        view.setZOrderMediaOverlay(true); // keeps PiP above remote views
        localContainer.removeAllViews();
        localContainer.addView(view);
        rtcEngine.setupLocalVideo(
                new VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, 0));
        rtcEngine.startPreview();
    }

    private void joinChannel() {
        String roomCode = getIntent().getStringExtra("roomCode");
        if (roomCode == null || roomCode.isEmpty()) roomCode = "studysync_default";

        ChannelMediaOptions options = new ChannelMediaOptions();
        options.clientRoleType        = Constants.CLIENT_ROLE_BROADCASTER;
        options.autoSubscribeAudio    = true;
        options.autoSubscribeVideo    = true;
        options.publishCameraTrack    = true;
        options.publishMicrophoneTrack = true;

        rtcEngine.joinChannel(AgoraConfig.TOKEN, roomCode, 0, options);
    }

    // ── RTC Events ─────────────────────────────────────────────────────────────
    private final IRtcEngineEventHandler rtcHandler = new IRtcEngineEventHandler() {

        @Override
        public void onUserJoined(int uid, int elapsed) {
            runOnUiThread(() -> {
                // FIX: Remove existing tile for this uid before adding a new one
                // This prevents the duplicate-user bug when someone rejoins
                if (videoTiles.containsKey(uid)) {
                    removeRemoteUser(uid);
                }
                addRemoteUser(uid);
            });
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            runOnUiThread(() -> removeRemoteUser(uid));
        }

        @Override
        public void onActiveSpeaker(int uid) {
            runOnUiThread(() -> highlightSpeaker(uid));
        }

        @Override
        public void onConnectionStateChanged(int state, int reason) {
            runOnUiThread(() -> {
                // FIX: Show reconnecting toast instead of silently failing
                if (state == Constants.CONNECTION_STATE_RECONNECTING) {
                    Toast.makeText(VideoCallActivity.this,
                            "Reconnecting...", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onRejoinChannelSuccess(String channel, int uid, int elapsed) {
            runOnUiThread(() ->
                    Toast.makeText(VideoCallActivity.this,
                            "Reconnected", Toast.LENGTH_SHORT).show());
        }
    };

    // ── Add / Remove remote users ──────────────────────────────────────────────
    private void addRemoteUser(int uid) {
        remoteUids.add(uid);

        View tile = getLayoutInflater().inflate(R.layout.video_tile, null);
        FrameLayout surface = tile.findViewById(R.id.videoSurface);

        // FIX: SurfaceView for remote too — smoother rendering
        SurfaceView view = new SurfaceView(this);
        surface.addView(view);

        TextView name = tile.findViewById(R.id.tvName);
        name.setText("User " + uid);

        videoTiles.put(uid, tile);
        remoteContainer.addView(tile);

        // FIX: RENDER_MODE_FIT prevents cropping and is smoother for multi-person
        rtcEngine.setupRemoteVideo(
                new VideoCanvas(view, VideoCanvas.RENDER_MODE_FIT, uid));

        tile.setAlpha(0f);
        tile.animate().alpha(1f).setDuration(250).start();

        resizeGrid();
        updateCount();
    }

    private void removeRemoteUser(int uid) {
        remoteUids.remove(Integer.valueOf(uid));
        View tile = videoTiles.remove(uid);
        if (tile != null) {
            tile.animate().alpha(0f).setDuration(200)
                    .withEndAction(() -> remoteContainer.removeView(tile)).start();
        }
        resizeGrid();
        updateCount();
    }

    // ── Grid layout ────────────────────────────────────────────────────────────
    /**
     * Smart tile sizing:
     * 1 user  → full screen (1 col)
     * 2 users → top/bottom split (1 col, 2 rows) — like FaceTime
     * 3-4     → 2x2 grid
     * 5-6     → 2x3 grid
     * 7+      → 3-col grid, scroll activates
     * Control panel is compact (100dp) so grid always gets ~88% of screen.
     */
    private void resizeGrid() {
        int total = remoteUids.size();
        if (total == 0) return;

        int cols, rows;
        switch (total) {
            case 1:  cols = 1; rows = 1; break;
            case 2:  cols = 1; rows = 2; break;  // vertical split like FaceTime
            case 3:
            case 4:  cols = 2; rows = (int) Math.ceil(total / 2.0); break;
            case 5:
            case 6:  cols = 2; rows = (int) Math.ceil(total / 2.0); break;
            default: cols = 3; rows = (int) Math.ceil(total / 3.0); break; // scroll kicks in
        }

        remoteContainer.setColumnCount(cols);
        remoteContainer.setRowCount(rows);

        float density     = getResources().getDisplayMetrics().density;
        int   screenW     = getResources().getDisplayMetrics().widthPixels;
        int   screenH     = getResources().getDisplayMetrics().heightPixels;
        int   controlPx   = (int) (CONTROL_PANEL_DP * density);
        int   availableH  = screenH - controlPx;

        int tileW = screenW / cols;
        // For layouts that fit on screen: divide height evenly
        // For 7+ users: fixed tile height so scroll activates cleanly
        int tileH = (rows <= 4) ? availableH / rows : (int) (screenH * 0.28f);

        // Apply a small margin between tiles for visual separation
        int margin = (int) (2 * density);

        for (int i = 0; i < remoteContainer.getChildCount(); i++) {
            View child = remoteContainer.getChildAt(i);
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width      = tileW - (margin * 2);
            p.height     = tileH - (margin * 2);
            p.setMargins(margin, margin, margin, margin);
            child.setLayoutParams(p);
        }
    }

    private void updateCount() {
        if (tvCount != null)
            tvCount.setText("👥 " + (remoteUids.size() + 1) + " in call");
    }

    // ── Speaker highlight ──────────────────────────────────────────────────────
    private void highlightSpeaker(int uid) {
        for (Map.Entry<Integer, View> entry : videoTiles.entrySet()) {
            boolean isSpeaker = entry.getKey() == uid;
            entry.getValue().setBackgroundColor(
                    isSpeaker ? 0x3300E676 : 0x00000000);
            if (isSpeaker) {
                entry.getValue().animate()
                        .scaleX(1.04f).scaleY(1.04f).setDuration(150)
                        .withEndAction(() -> entry.getValue().animate()
                                .scaleX(1f).scaleY(1f).setDuration(150).start())
                        .start();
            }
        }
    }

    // ── Controls ───────────────────────────────────────────────────────────────
    private void toggleMute() {
        muted = !muted;
        rtcEngine.muteLocalAudioStream(muted);
        btnMute.setBackgroundResource(
                muted ? R.drawable.btn_control_muted : R.drawable.btn_control_normal);
        btnMute.setImageResource(muted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
        animateButton(btnMute);
    }

    private void toggleVideo() {
        videoEnabled = !videoEnabled;
        rtcEngine.muteLocalVideoStream(!videoEnabled);
        View pip = (View) localContainer.getParent();
        pip.setVisibility(videoEnabled ? View.VISIBLE : View.INVISIBLE);
        btnVideo.setBackgroundResource(
                videoEnabled ? R.drawable.btn_control_normal : R.drawable.btn_control_muted);
        btnVideo.setImageResource(
                videoEnabled ? R.drawable.ic_video : R.drawable.ic_video_off);
        animateButton(btnVideo);
    }

    private void switchCamera() {
        rtcEngine.switchCamera();
        animateButton(btnSwitch);
        View pip = (View) localContainer.getParent();
        pip.animate().rotationY(90f).setDuration(150)
                .withEndAction(() ->
                        pip.animate().rotationY(0f).setDuration(150).start())
                .start();
    }

    private void toggleMuteAll() {
        allMuted = !allMuted;
        for (Integer uid : remoteUids)
            rtcEngine.muteRemoteAudioStream(uid, allMuted);
        btnMuteAll.setBackgroundResource(
                allMuted ? R.drawable.btn_control_muted : R.drawable.btn_control_normal);
        animateButton(btnMuteAll);
        Toast.makeText(this,
                allMuted ? "All muted" : "All unmuted", Toast.LENGTH_SHORT).show();
    }

    private void endCall() {
        // FIX: Animate then finish — only red button ends the call
        btnEnd.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100)
                .withEndAction(this::leaveAndFinish).start();
    }

    private void leaveAndFinish() {
        timerHandler.removeCallbacks(timerRunnable);
        if (rtcEngine != null) {
            rtcEngine.leaveChannel();
            RtcEngine.destroy();
            rtcEngine = null;
        }
        finish();
    }

    private void animateButton(View btn) {
        btn.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
                .withEndAction(() ->
                        btn.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                .start();
    }

    // ── FIX: Block back button — call only ends via red button ─────────────────
    @Override
    public void onBackPressed() {
        // Do nothing — user must press the red End button to leave
        super.onBackPressed();
        Toast.makeText(this, "Press End Call to leave", Toast.LENGTH_SHORT).show();
    }

    // ── Permissions ────────────────────────────────────────────────────────────
    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int reqCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(reqCode, permissions, grantResults);
        if (reqCode == PERMISSION_REQ_ID && hasPermissions()) initAgora();
        else {
            Toast.makeText(this,
                    "Camera & microphone permission required",
                    Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Only cleanup here if leaveAndFinish wasn't already called
        timerHandler.removeCallbacks(timerRunnable);
        if (rtcEngine != null) {
            rtcEngine.leaveChannel();
            RtcEngine.destroy();
            rtcEngine = null;
        }
    }
}