package com.example.studysync;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.*;

import io.agora.rtc2.*;
import io.agora.rtc2.video.VideoCanvas;
import io.agora.rtc2.ChannelMediaOptions;

public class VideoCallActivity extends AppCompatActivity {

    private static final int PERMISSION_REQ_ID = 22;

    private GridLayout remoteContainer;
    private FrameLayout localContainer;
    private TextView tvCount;

    private ImageButton btnMute, btnVideo, btnEnd, btnSwitch, btnMuteAll;

    private RtcEngine rtcEngine;
    private boolean muted = false, videoEnabled = true;

    private final List<Integer> remoteUids = new ArrayList<>();
    private final Map<Integer, View> videoTiles = new HashMap<>();

    // ================= ACTIVITY =================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call);

        initUI();

        if (hasPermissions()) initAgora();
        else ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQ_ID);
    }

    private void initUI() {

        remoteContainer = findViewById(R.id.remoteVideoContainer);
        localContainer = findViewById(R.id.localVideoContainer);
        tvCount = findViewById(R.id.tvParticipantCount);

        btnMute = findViewById(R.id.btnMute);
        btnVideo = findViewById(R.id.btnVideo);
        btnEnd = findViewById(R.id.btnEndCall);
        btnSwitch = findViewById(R.id.btnSwitchCamera);
        btnMuteAll = findViewById(R.id.btnMuteAll);

        btnMute.setOnClickListener(v -> toggleMute());
        btnVideo.setOnClickListener(v -> toggleVideo());
        btnSwitch.setOnClickListener(v -> rtcEngine.switchCamera());
        btnEnd.setOnClickListener(v -> finish());
        btnMuteAll.setOnClickListener(v -> muteAllParticipants());
    }

    // ================= AGORA =================

    private void initAgora() {

        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = getApplicationContext();
            config.mAppId = AgoraConfig.APP_ID;
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
                new VideoCanvas(view,
                        VideoCanvas.RENDER_MODE_HIDDEN,
                        0));
    }

    private void joinChannel() {

        ChannelMediaOptions options =
                new ChannelMediaOptions();

        options.clientRoleType =
                Constants.CLIENT_ROLE_BROADCASTER;

        rtcEngine.joinChannel(
                AgoraConfig.TOKEN,
                "testRoom",
                0,
                options);
    }

    // ================= EVENTS =================

    private final IRtcEngineEventHandler rtcHandler =
            new IRtcEngineEventHandler() {

                @Override
                public void onUserJoined(int uid, int elapsed) {
                    runOnUiThread(() -> addRemoteUser(uid));
                }

                @Override
                public void onUserOffline(int uid, int reason) {
                    runOnUiThread(() -> removeRemoteUser(uid));
                }

                @Override
                public void onActiveSpeaker(int uid) {
                    runOnUiThread(() -> highlightSpeaker(uid));
                }
            };

    // ================= ADD USER =================

    private void addRemoteUser(int uid) {

        if (remoteUids.contains(uid)) return;
        remoteUids.add(uid);

        View tile = getLayoutInflater()
                .inflate(R.layout.video_tile, null);

        FrameLayout surface =
                tile.findViewById(R.id.videoSurface);

        TextureView view = new TextureView(this);
        surface.addView(view);

        TextView name =
                tile.findViewById(R.id.tvName);
        name.setText("User " + uid);

        videoTiles.put(uid, tile);
        remoteContainer.addView(tile);

        rtcEngine.setupRemoteVideo(
                new VideoCanvas(view,
                        VideoCanvas.RENDER_MODE_HIDDEN,
                        uid));

        resizeGrid();
        updateCount();
    }

    // ================= REMOVE USER =================

    private void removeRemoteUser(int uid) {

        remoteUids.remove(Integer.valueOf(uid));

        View tile = videoTiles.get(uid);
        if (tile != null) {
            remoteContainer.removeView(tile);
            videoTiles.remove(uid);
        }

        resizeGrid();
        updateCount();
    }

    // ================= GRID RESIZE =================

    private void resizeGrid() {

        int total = remoteUids.size();

        int cols = (int) Math.ceil(Math.sqrt(total));
        int rows = (int) Math.ceil((double) total / cols);

        remoteContainer.setColumnCount(cols);
        remoteContainer.setRowCount(rows);

        for (int i = 0;
             i < remoteContainer.getChildCount();
             i++) {

            View child =
                    remoteContainer.getChildAt(i);

            GridLayout.LayoutParams params =
                    new GridLayout.LayoutParams();

            params.width =
                    getResources().getDisplayMetrics().widthPixels / cols;

            params.height =
                    getResources().getDisplayMetrics().heightPixels / rows;

            child.setLayoutParams(params);
        }
    }

    private void updateCount() {
        tvCount.setText("👥 " + (remoteUids.size() + 1) + " in call");
    }

    // ================= SPEAKER =================

    private void highlightSpeaker(int uid) {

        for (Map.Entry<Integer, View> entry :
                videoTiles.entrySet()) {

            View tile = entry.getValue();

            if (entry.getKey() == uid) {
                tile.setBackgroundColor(0xFF00FF00);
                animateSpeaker(tile);
            } else {
                tile.setBackgroundColor(0xFF000000);
            }
        }
    }

    private void animateSpeaker(View tile) {

        tile.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(150)
                .withEndAction(() ->
                        tile.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(150));
    }

    // ================= CONTROLS =================

    private void toggleMute() {
        muted = !muted;
        rtcEngine.muteLocalAudioStream(muted);
    }

    private void toggleVideo() {
        videoEnabled = !videoEnabled;
        rtcEngine.muteLocalVideoStream(!videoEnabled);
        localContainer.setVisibility(
                videoEnabled ? View.VISIBLE : View.INVISIBLE);
    }

    private void muteAllParticipants() {

        for (Integer uid : remoteUids) {
            rtcEngine.muteRemoteAudioStream(uid, true);
        }

        Toast.makeText(this,
                "All participants muted",
                Toast.LENGTH_SHORT).show();
    }

    // ================= PERMISSION =================

    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ================= DESTROY =================

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (rtcEngine != null) {
            rtcEngine.leaveChannel();
            RtcEngine.destroy();
            rtcEngine = null;
        }
    }
}
