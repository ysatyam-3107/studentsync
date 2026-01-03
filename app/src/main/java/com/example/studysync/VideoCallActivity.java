package com.example.studysync;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.video.VideoCanvas;
import io.agora.rtc2.ChannelMediaOptions;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.HashMap;
import java.util.Map;

public class VideoCallActivity extends AppCompatActivity {

    private static final String TAG = "VideoCallActivity";
    private static final int PERMISSION_REQ_ID = 22;

    private static final String[] PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    private RtcEngine rtcEngine;
    private String roomCode;
    private String userName;
    private int localUid;
    private int remoteUid = -1;

    private FrameLayout localContainer, remoteContainer;
    private ImageButton btnMute, btnVideo, btnEnd, btnSwitch;
    private TextView tvStatus, tvCount;

    private boolean muted = false;
    private boolean videoEnabled = true;

    private DatabaseReference callRef;

    // ======================= ACTIVITY =======================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call);

        roomCode = getIntent().getStringExtra("roomCode");
        userName = getIntent().getStringExtra("userName");

        if (roomCode == null) {
            Toast.makeText(this, "Invalid room", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initUI();
        initFirebase();

        if (hasPermissions()) {
            initAgora();
        } else {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQ_ID);
        }
    }

    // ======================= UI =======================

    private void initUI() {
        localContainer = findViewById(R.id.localVideoContainer);
        remoteContainer = findViewById(R.id.remoteVideoContainer);

        btnMute = findViewById(R.id.btnMute);
        btnVideo = findViewById(R.id.btnVideo);
        btnEnd = findViewById(R.id.btnEndCall);
        btnSwitch = findViewById(R.id.btnSwitchCamera);

        tvStatus = findViewById(R.id.tvCallStatus);
        tvCount = findViewById(R.id.tvParticipantCount);

        btnMute.setOnClickListener(v -> toggleMute());
        btnVideo.setOnClickListener(v -> toggleVideo());
        btnSwitch.setOnClickListener(v -> rtcEngine.switchCamera());
        btnEnd.setOnClickListener(v -> showEndDialog());
    }

    // ======================= PERMISSIONS =======================

    private boolean hasPermissions() {
        for (String p : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == PERMISSION_REQ_ID && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            initAgora();
        } else {
            Toast.makeText(this, "Camera & Mic required", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    // ======================= AGORA =======================

    private void initAgora() {
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = getApplicationContext();
            config.mAppId = AgoraConfig.APP_ID;
            config.mEventHandler = rtcHandler;

            rtcEngine = RtcEngine.create(config);

            rtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION);
            rtcEngine.enableVideo();

            setupLocalVideo();
            joinChannel();

        } catch (Exception e) {
            Log.e(TAG, "Agora init error", e);
            finish();
        }
    }

    private void setupLocalVideo() {
        TextureView view = new TextureView(this);
        localContainer.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        rtcEngine.setupLocalVideo(
                new VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, 0)
        );
        rtcEngine.startPreview();
    }

    private void joinChannel() {
        localUid = Math.abs(
                FirebaseAuth.getInstance().getCurrentUser().getUid().hashCode()
        );

        ChannelMediaOptions options = new ChannelMediaOptions();
        options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
        options.autoSubscribeAudio = true;
        options.autoSubscribeVideo = true;
        options.publishCameraTrack = true;
        options.publishMicrophoneTrack = true;

        rtcEngine.joinChannel(
                AgoraConfig.TOKEN,
                roomCode,
                0,
                options
        );

        tvStatus.setText("Connecting...");
    }

    // ======================= AGORA CALLBACKS =======================

    private final IRtcEngineEventHandler rtcHandler = new IRtcEngineEventHandler() {

        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            runOnUiThread(() -> tvStatus.setText("Connected"));
        }

        @Override
        public void onUserJoined(int uid, int elapsed) {
            runOnUiThread(() -> {
                remoteUid = uid;
                setupRemoteVideo(uid);
            });
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            runOnUiThread(() -> {
                if (uid == remoteUid) {
                    remoteUid = -1;
                    remoteContainer.removeAllViews();
                    tvStatus.setText("Waiting for others...");
                }
            });
        }

        @Override
        public void onError(int err) {
            Log.e(TAG, "Agora error: " + err);
        }
    };

    private void setupRemoteVideo(int uid) {
        remoteContainer.removeAllViews();

        TextureView view = new TextureView(this);
        remoteContainer.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        rtcEngine.setupRemoteVideo(
                new VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, uid)
        );

        remoteContainer.setVisibility(View.VISIBLE);
    }

    // ======================= CONTROLS =======================

    private void toggleMute() {
        muted = !muted;
        rtcEngine.muteLocalAudioStream(muted);
    }

    private void toggleVideo() {
        videoEnabled = !videoEnabled;
        rtcEngine.muteLocalVideoStream(!videoEnabled);
        localContainer.setVisibility(videoEnabled ? View.VISIBLE : View.INVISIBLE);
    }

    private void showEndDialog() {
        new AlertDialog.Builder(this)
                .setTitle("End Call")
                .setMessage("Do you want to end the call?")
                .setPositiveButton("End", (d, w) -> endCall())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void endCall() {
        rtcEngine.leaveChannel();
        updateFirebase(false);
        finish();
    }

    // ======================= FIREBASE =======================

    private void initFirebase() {
        callRef = FirebaseDatabase.getInstance()
                .getReference("Calls")
                .child(roomCode);

        updateFirebase(true);

        callRef.child("participants")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        tvCount.setText("👥 " + snap.getChildrenCount() + " in call");
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void updateFirebase(boolean join) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (join) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", userName);
            map.put("joinedAt", ServerValue.TIMESTAMP);
            callRef.child("participants").child(uid).setValue(map);
        } else {
            callRef.child("participants").child(uid).removeValue();
        }
    }

    // ======================= LIFECYCLE =======================

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (rtcEngine != null) {
            rtcEngine.leaveChannel();
            rtcEngine.stopPreview();
            RtcEngine.destroy();
            rtcEngine = null;
        }
        updateFirebase(false);
    }
}