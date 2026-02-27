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

    private static final int PERMISSION_REQ_ID = 22;

    private GridLayout remoteContainer;
    private FrameLayout localContainer;
    private TextView tvCount, tvCallDuration;

    private ImageButton btnMute, btnVideo, btnEnd, btnSwitch, btnMuteAll;

    private RtcEngine rtcEngine;
    private boolean muted = false;
    private boolean videoEnabled = true;
    private boolean allMuted = false;

    private final List<Integer> remoteUids = new ArrayList<>();
    private final Map<Integer, View> videoTiles = new HashMap<>();

    // Call duration timer
    private final Handler timerHandler = new Handler();
    private int secondsElapsed = 0;
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            secondsElapsed++;
            int min = secondsElapsed / 60;
            int sec = secondsElapsed % 60;
            if (tvCallDuration != null) {
                tvCallDuration.setText(String.format(Locale.getDefault(), "%02d:%02d", min, sec));
            }
            timerHandler.postDelayed(this, 1000);
        }
    };

    // ─────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call);

        // Hide system ActionBar — full screen call UI
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        initUI();
        startCallTimer();

        if (hasPermissions()) initAgora();
        else ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQ_ID);
    }

    private void initUI() {
        remoteContainer  = findViewById(R.id.remoteVideoContainer);
        localContainer   = ((androidx.cardview.widget.CardView)
                findViewById(R.id.localVideoContainer))
                .findViewById(android.R.id.content) != null
                ? (FrameLayout) ((androidx.cardview.widget.CardView)
                findViewById(R.id.localVideoContainer)).getChildAt(0)
                : new FrameLayout(this);

        // Re-bind localContainer properly
        localContainer   = (FrameLayout) ((androidx.cardview.widget.CardView)
                findViewById(R.id.localVideoContainer)).getChildAt(0);

        tvCount          = findViewById(R.id.tvParticipantCount);
        tvCallDuration   = findViewById(R.id.tvCallDuration);

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

    // ─────────────── TIMER ───────────────────
    private void startCallTimer() {
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    // ─────────────── AGORA ───────────────────
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

    // ─────────────── RTC EVENTS ──────────────
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

    // ─────────────── ADD / REMOVE USER ───────
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

        // Animate tile entrance
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

    // ─────────────── GRID RESIZE ─────────────
    private void resizeGrid() {
        int total = remoteUids.size();
        if (total == 0) return;

        int cols = (int) Math.ceil(Math.sqrt(total));
        int rows = (int) Math.ceil((double) total / cols);

        remoteContainer.setColumnCount(Math.max(cols, 1));
        remoteContainer.setRowCount(Math.max(rows, 1));

        int w = getResources().getDisplayMetrics().widthPixels / Math.max(cols, 1);
        int h = getResources().getDisplayMetrics().heightPixels / Math.max(rows, 1);

        for (int i = 0; i < remoteContainer.getChildCount(); i++) {
            View child = remoteContainer.getChildAt(i);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width  = w;
            params.height = h;
            child.setLayoutParams(params);
        }
    }

    private void updateCount() {
        int total = remoteUids.size() + 1;
        tvCount.setText("👥 " + total + " in call");
    }

    // ─────────────── SPEAKER HIGHLIGHT ───────
    private void highlightSpeaker(int uid) {
        for (Map.Entry<Integer, View> entry : videoTiles.entrySet()) {
            View tile = entry.getValue();
            if (entry.getKey() == uid) {
                tile.setBackgroundColor(0x3300E676); // subtle green glow
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

    // ─────────────── CONTROLS ────────────────
    private void toggleMute() {
        muted = !muted;
        rtcEngine.muteLocalAudioStream(muted);

        // Visual feedback: red tinted when muted
        btnMute.setBackgroundResource(
                muted ? R.drawable.btn_control_muted : R.drawable.btn_control_normal);
        btnMute.setImageResource(
                muted ? R.drawable.ic_mic_off : R.drawable.ic_mic);

        animateButton(btnMute);
    }

    private void toggleVideo() {
        videoEnabled = !videoEnabled;
        rtcEngine.muteLocalVideoStream(!videoEnabled);

        // Hide/show local preview
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
        // Flip animation on local preview
        ((View) localContainer.getParent()).animate()
                .rotationY(90f).setDuration(150)
                .withEndAction(() ->
                        ((View) localContainer.getParent()).animate()
                                .rotationY(0f).setDuration(150).start())
                .start();
    }

    private void toggleMuteAll() {
        allMuted = !allMuted;
        for (Integer uid : remoteUids) {
            rtcEngine.muteRemoteAudioStream(uid, allMuted);
        }
        btnMuteAll.setBackgroundResource(
                allMuted ? R.drawable.btn_control_muted : R.drawable.btn_control_secondary);
        animateButton(btnMuteAll);

        Toast.makeText(this,
                allMuted ? "All participants muted" : "All participants unmuted",
                Toast.LENGTH_SHORT).show();
    }

    private void endCall() {
        // Scale down animation before finishing
        btnEnd.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100)
                .withEndAction(this::finish).start();
    }

    // Bounce micro-animation on button tap
    private void animateButton(View btn) {
        btn.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
                .withEndAction(() ->
                        btn.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                .start();
    }

    // ─────────────── PERMISSIONS ─────────────
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
        if (requestCode == PERMISSION_REQ_ID && hasPermissions()) {
            initAgora();
        } else {
            Toast.makeText(this,
                    "Camera & microphone permission required", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // ─────────────── LIFECYCLE ───────────────
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