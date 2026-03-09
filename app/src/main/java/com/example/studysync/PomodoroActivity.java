package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

public class PomodoroActivity extends AppCompatActivity {

    // ── Views ──────────────────────────────────────────────────────────────────
    private TextView       tvTimer, tvStatus, tvTimerType, tvRoomName, tvSessionCount;
    private MaterialButton btnStartPause, btnReset, btnSkip, btnSettings;
    private ProgressBar    circularProgress;

    // ── Timer state ────────────────────────────────────────────────────────────
    private CountDownTimer countDownTimer;
    private boolean        isRunning   = false;
    private boolean        isHost      = false;
    private boolean        isBreak     = false;
    private int            workDuration  = 25;
    private int            breakDuration = 5;
    private long           remainingMillis = 0;
    private long           totalSessionMillis = 0; // for progress %
    private int            sessionCount = 0;       // completed pomodoros

    // ── Firebase ───────────────────────────────────────────────────────────────
    private String             roomCode;
    private DatabaseReference  timerRef;
    private ValueEventListener timerListener;

    // ── Colors ─────────────────────────────────────────────────────────────────
    private static final int COLOR_WORK  = 0xFF4CAF50; // green
    private static final int COLOR_BREAK = 0xFF2196F3; // blue
    private static final int COLOR_PAUSE = 0xFFAAAAAA; // grey

    // ──────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pomodoro);

        roomCode = getIntent().getStringExtra("roomCode");
        if (roomCode == null) { finish(); return; }

        initViews();

        timerRef = FirebaseDatabase.getInstance()
                .getReference("Rooms").child(roomCode).child("timer");

        loadRoomName();
        checkHost();
        listenForTimer();
        loadTimerSettings();
    }

    // ── View wiring ────────────────────────────────────────────────────────────
    private void initViews() {
        tvTimer        = findViewById(R.id.tvTimer);
        tvStatus       = findViewById(R.id.tvStatus);
        tvTimerType    = findViewById(R.id.tvTimerType);
        tvRoomName     = findViewById(R.id.tvRoomName);
        tvSessionCount = findViewById(R.id.tvSessionCount);
        circularProgress = findViewById(R.id.circularProgress);
        btnStartPause  = findViewById(R.id.btnStartPause);
        btnReset       = findViewById(R.id.btnReset);
        btnSkip        = findViewById(R.id.btnSkip);
        btnSettings    = findViewById(R.id.btnSettings);

        btnStartPause.setOnClickListener(v -> {
            if (isRunning) pauseTimer(); else startTimer();
        });
        btnReset.setOnClickListener(v    -> resetTimer());
        btnSkip.setOnClickListener(v     -> skipSession());
        btnSettings.setOnClickListener(v -> showSettingsDialog());

        // Show loading state until Firebase responds
        tvStatus.setText("Loading...");
        tvTimer.setText("--:--");
    }

    // ── Load room name for subtitle ────────────────────────────────────────────
    private void loadRoomName() {
        FirebaseDatabase.getInstance().getReference("Rooms")
                .child(roomCode).child("roomName")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot s) {
                        String name = s.getValue(String.class);
                        if (tvRoomName != null)
                            tvRoomName.setText(name != null ? name : roomCode);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    // ── Host check ─────────────────────────────────────────────────────────────
    private void checkHost() {
        FirebaseDatabase.getInstance().getReference("Rooms")
                .child(roomCode).child("createdBy")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot s) {
                        String creator = s.getValue(String.class);
                        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
                        String myUid  = FirebaseAuth.getInstance().getCurrentUser().getUid();
                        isHost = myUid.equals(creator);

                        btnStartPause.setEnabled(isHost);
                        btnReset.setEnabled(isHost);
                        btnSkip.setEnabled(isHost);
                        btnSettings.setEnabled(isHost);

                        if (!isHost) {
                            tvStatus.setText("Host controls the timer");
                            btnSettings.setVisibility(View.GONE);
                            btnSkip.setVisibility(View.GONE);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    // ── Start timer ────────────────────────────────────────────────────────────
    private void startTimer() {
        if (!isHost) return;
        if (remainingMillis <= 0)
            remainingMillis = (isBreak ? breakDuration : workDuration) * 60L * 1000L;

        long endTime = System.currentTimeMillis() + remainingMillis;
        timerRef.child("running").setValue(true);
        timerRef.child("endTime").setValue(endTime);
        timerRef.child("isBreak").setValue(isBreak);
        timerRef.child("sessionCount").setValue(sessionCount);
    }

    // ── Pause timer ────────────────────────────────────────────────────────────
    private void pauseTimer() {
        if (!isHost) return;
        if (countDownTimer != null) countDownTimer.cancel();
        // Store remaining so resume picks up from here
        timerRef.child("running").setValue(false);
        timerRef.child("remainingMillis").setValue(remainingMillis);
    }

    // ── Reset timer ────────────────────────────────────────────────────────────
    private void resetTimer() {
        if (!isHost) return;
        if (countDownTimer != null) countDownTimer.cancel();
        remainingMillis = (isBreak ? breakDuration : workDuration) * 60L * 1000L;
        timerRef.child("running").setValue(false);
        timerRef.child("endTime").setValue(0);
        timerRef.child("remainingMillis").setValue(remainingMillis);
        timerRef.child("isBreak").setValue(isBreak);
        updateTimerUI(remainingMillis);
        setRunningState(false);
        tvStatus.setText("Reset");
    }

    // ── Skip to next session ───────────────────────────────────────────────────
    private void skipSession() {
        if (!isHost) return;
        if (countDownTimer != null) countDownTimer.cancel();
        doSessionSwitch();
    }

    // ── Firebase listener ──────────────────────────────────────────────────────
    private void listenForTimer() {
        timerListener = timerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean running      = snapshot.child("running").getValue(Boolean.class);
                Long    endTime      = snapshot.child("endTime").getValue(Long.class);
                Long    savedRemaining = snapshot.child("remainingMillis").getValue(Long.class);
                Boolean breakState   = snapshot.child("isBreak").getValue(Boolean.class);
                Integer savedSessions = snapshot.child("sessionCount").getValue(Integer.class);

                if (running      == null) running      = false;
                if (endTime      == null) endTime      = 0L;
                if (breakState   == null) breakState   = false;
                if (savedSessions != null) sessionCount = savedSessions;

                isBreak = breakState;

                if (running) {
                    isRunning = true;
                    startLocalTimer(endTime);
                    setRunningState(true);
                    tvStatus.setText(isBreak ? "☕ Break Time" : "🎯 Focus Time");
                } else {
                    isRunning = false;
                    if (countDownTimer != null) countDownTimer.cancel();
                    setRunningState(false);

                    // Resume from saved remaining if available
                    if (savedRemaining != null && savedRemaining > 0) {
                        remainingMillis = savedRemaining;
                        updateTimerUI(remainingMillis);
                    } else if (remainingMillis <= 0) {
                        remainingMillis = (isBreak ? breakDuration : workDuration) * 60L * 1000L;
                        updateTimerUI(remainingMillis);
                    }

                    tvStatus.setText(isHost ? "Ready" : "Host controls the timer");
                }

                updateSessionCountUI();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    // ── Local countdown ────────────────────────────────────────────────────────
    private void startLocalTimer(long endTime) {
        if (countDownTimer != null) countDownTimer.cancel();

        remainingMillis = endTime - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            doSessionSwitch();
            return;
        }

        totalSessionMillis = (isBreak ? breakDuration : workDuration) * 60L * 1000L;

        countDownTimer = new CountDownTimer(remainingMillis, 1000) {
            @Override
            public void onTick(long ms) {
                remainingMillis = ms;
                updateTimerUI(ms);
                updateCircularProgress(ms);
            }
            @Override
            public void onFinish() {
                remainingMillis = 0;
                updateTimerUI(0);
                updateCircularProgress(0);
                if (isHost) doSessionSwitch();
                else {
                    // Non-host: just show session ended, wait for Firebase update
                    tvStatus.setText("Session ended...");
                    playAlertSound();
                    vibrate();
                }
            }
        }.start();
    }

    // ── Session switch logic ───────────────────────────────────────────────────
    private void doSessionSwitch() {
        // Increment count only when a work session ends
        if (!isBreak) sessionCount++;

        isBreak = !isBreak;

        timerRef.child("isBreak").setValue(isBreak);
        timerRef.child("running").setValue(false);
        timerRef.child("remainingMillis").setValue(0);
        timerRef.child("sessionCount").setValue(sessionCount);

        remainingMillis = (isBreak ? breakDuration : workDuration) * 60L * 1000L;
        updateTimerUI(remainingMillis);
        setRunningState(false);
        updateSessionCountUI();
        animateTimerColor(isBreak ? COLOR_BREAK : COLOR_WORK);
        playAlertSound();
        vibrate();

        Toast.makeText(this,
                isBreak ? "☕ Break time! Great work!" : "🎯 Focus time! Let's go!",
                Toast.LENGTH_LONG).show();
    }

    // ── Update timer text + type label ─────────────────────────────────────────
    private void updateTimerUI(long millis) {
        int minutes = (int) (millis / 1000) / 60;
        int seconds = (int) (millis / 1000) % 60;
        tvTimer.setText(String.format("%02d:%02d", minutes, seconds));
        tvTimerType.setText(isBreak
                ? "☕ Break (" + breakDuration + " min)"
                : "🎯 Work (" + workDuration + " min)");
        tvTimer.setTextColor(isBreak ? COLOR_BREAK : COLOR_WORK);
    }

    // ── Circular progress update ───────────────────────────────────────────────
    private void updateCircularProgress(long remainingMs) {
        if (totalSessionMillis <= 0) return;
        // Progress = elapsed % → 0 at start, 100 at end
        int elapsed = (int) (((totalSessionMillis - remainingMs) * 100) / totalSessionMillis);
        ObjectAnimator.ofInt(circularProgress, "progress", circularProgress.getProgress(), elapsed)
                .setDuration(800)
                .start();
    }

    // ── Session count display ──────────────────────────────────────────────────
    private void updateSessionCountUI() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sessionCount; i++) sb.append("🍅");
        if (sessionCount == 0) sb.append("No sessions yet");
        tvSessionCount.setText(sb.toString());
    }

    // ── Start/pause button toggle ──────────────────────────────────────────────
    private void setRunningState(boolean running) {
        isRunning = running;
        if (running) {
            btnStartPause.setText("⏸ Pause");
            btnStartPause.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFE53935));
        } else {
            btnStartPause.setText("▶ Start");
            btnStartPause.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF4CAF50));
        }
    }

    // ── Animate timer text color ───────────────────────────────────────────────
    private void animateTimerColor(int toColor) {
        int fromColor = (Integer) tvTimer.getTag() != null
                ? (int) tvTimer.getTag() : COLOR_WORK;
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
        anim.setDuration(800);
        anim.addUpdateListener(a -> tvTimer.setTextColor((int) a.getAnimatedValue()));
        anim.start();
        tvTimer.setTag(toColor);
    }

    // ── Load settings dialog ───────────────────────────────────────────────────
    private void loadTimerSettings() {
        FirebaseDatabase.getInstance().getReference("Rooms")
                .child(roomCode).child("timerSettings")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot s) {
                        Integer work  = s.child("workDuration").getValue(Integer.class);
                        Integer brk   = s.child("breakDuration").getValue(Integer.class);
                        if (work != null) workDuration  = work;
                        if (brk  != null) breakDuration = brk;
                        remainingMillis = workDuration * 60L * 1000L;
                        totalSessionMillis = remainingMillis;
                        updateTimerUI(remainingMillis);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    // ── Settings dialog ────────────────────────────────────────────────────────
    private void showSettingsDialog() {
        View view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_timer_settings, null);

        SeekBar sbWork  = view.findViewById(R.id.sbWorkDuration);
        SeekBar sbBreak = view.findViewById(R.id.sbBreakDuration);
        TextView tvWorkVal  = view.findViewById(R.id.tvWorkValue);
        TextView tvBreakVal = view.findViewById(R.id.tvBreakValue);
        TextView tvPreview  = view.findViewById(R.id.tvSettingsPreview);

        // SeekBar range: 1–60 min, offset by 1
        sbWork.setMax(59);
        sbBreak.setMax(59);
        sbWork.setProgress(workDuration - 1);
        sbBreak.setProgress(breakDuration - 1);
        tvWorkVal.setText(workDuration + " min");
        tvBreakVal.setText(breakDuration + " min");
        tvPreview.setText("Work " + workDuration + " min → Break " + breakDuration + " min");

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                int w = sbWork.getProgress()  + 1;
                int b = sbBreak.getProgress() + 1;
                tvWorkVal.setText(w + " min");
                tvBreakVal.setText(b + " min");
                tvPreview.setText("Work " + w + " min → Break " + b + " min");
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        };
        sbWork.setOnSeekBarChangeListener(listener);
        sbBreak.setOnSeekBarChangeListener(listener);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.DarkDialogTheme)
                .setView(view)
                .setTitle("Timer Settings")
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int newWork  = sbWork.getProgress()  + 1;
            int newBreak = sbBreak.getProgress() + 1;

            // Validation: 1–60 range already enforced by seekbar, just sanity check
            if (newWork < 1 || newWork > 60 || newBreak < 1 || newBreak > 60) {
                Toast.makeText(this, "Duration must be between 1 and 60 minutes",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            workDuration  = newWork;
            breakDuration = newBreak;

            DatabaseReference settingsRef = FirebaseDatabase.getInstance()
                    .getReference("Rooms").child(roomCode).child("timerSettings");
            settingsRef.child("workDuration").setValue(workDuration);
            settingsRef.child("breakDuration").setValue(breakDuration);

            remainingMillis    = workDuration * 60L * 1000L;
            totalSessionMillis = remainingMillis;
            updateTimerUI(remainingMillis);
            circularProgress.setProgress(0);
            dialog.dismiss();

            Toast.makeText(this, "Settings saved ✅", Toast.LENGTH_SHORT).show();
        });
    }

    // ── Alert sound on session end ─────────────────────────────────────────────
    private void playAlertSound() {
        try {
            Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            RingtoneManager.getRingtone(getApplicationContext(), sound).play();
        } catch (Exception ignored) {}
    }

    // ── Haptic vibration on session end ───────────────────────────────────────
    private void vibrate() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(
                            new long[]{0, 200, 100, 200}, -1));
                } else {
                    v.vibrate(new long[]{0, 200, 100, 200}, -1);
                }
            }
        } catch (Exception ignored) {}
    }

    // ── Cleanup ────────────────────────────────────────────────────────────────
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
        if (timerListener  != null) timerRef.removeEventListener(timerListener);
    }
}