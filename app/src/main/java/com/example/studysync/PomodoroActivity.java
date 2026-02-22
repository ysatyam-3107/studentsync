package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

public class PomodoroActivity extends AppCompatActivity {

    private TextView tvTimer, tvStatus, tvTimerType;
    private Button btnStart, btnStop, btnReset, btnSettings;

    private CountDownTimer countDownTimer;

    private String roomCode;
    private DatabaseReference timerRef;

    private boolean isHost = false;
    private boolean isBreak = false;

    private int workDuration = 25;
    private int breakDuration = 5;

    private long remainingMillis = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pomodoro);

        tvTimer = findViewById(R.id.tvTimer);
        tvStatus = findViewById(R.id.tvStatus);
        tvTimerType = findViewById(R.id.tvTimerType);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnReset = findViewById(R.id.btnReset);
        btnSettings = findViewById(R.id.btnSettings);

        roomCode = getIntent().getStringExtra("roomCode");

        if (roomCode == null) {
            finish();
            return;
        }

        timerRef = FirebaseDatabase.getInstance()
                .getReference("Rooms")
                .child(roomCode)
                .child("timer");

        checkHost();
        listenForTimer();
        loadTimerSettings();

        btnStart.setOnClickListener(v -> startTimer());
        btnStop.setOnClickListener(v -> pauseTimer());
        btnReset.setOnClickListener(v -> resetTimer());
        btnSettings.setOnClickListener(v -> showSettingsDialog());
    }

    // ===============================
    // HOST CHECK
    // ===============================

    private void checkHost() {
        DatabaseReference roomRef = FirebaseDatabase.getInstance()
                .getReference("Rooms")
                .child(roomCode);

        roomRef.child("createdBy").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String creator = snapshot.getValue(String.class);
                String myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

                isHost = myUid.equals(creator);

                btnStart.setEnabled(isHost);
                btnStop.setEnabled(isHost);
                btnReset.setEnabled(isHost);
                btnSettings.setEnabled(isHost);

                if (!isHost) {
                    tvStatus.setText("Host controls the timer");
                    btnSettings.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // ===============================
    // TIMER START
    // ===============================

    private void startTimer() {

        if (!isHost) return;

        if (remainingMillis == 0) {
            remainingMillis = (isBreak ? breakDuration : workDuration) * 60L * 1000L;
        }

        long endTime = System.currentTimeMillis() + remainingMillis;

        timerRef.child("running").setValue(true);
        timerRef.child("endTime").setValue(endTime);
        timerRef.child("isBreak").setValue(isBreak);
    }

    // ===============================
    // TIMER PAUSE (STOP)
    // ===============================

    private void pauseTimer() {

        if (!isHost) return;

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        timerRef.child("running").setValue(false);
    }

    // ===============================
    // TIMER RESET
    // ===============================

    private void resetTimer() {

        if (!isHost) return;

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        remainingMillis = (isBreak ? breakDuration : workDuration) * 60L * 1000L;

        timerRef.child("running").setValue(false);
        timerRef.child("endTime").setValue(0);
        timerRef.child("isBreak").setValue(isBreak);

        updateTimerUI(remainingMillis);
        tvStatus.setText("Reset");
    }

    // ===============================
    // LISTEN FIREBASE TIMER
    // ===============================

    private void listenForTimer() {

        timerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                Boolean running = snapshot.child("running").getValue(Boolean.class);
                Long endTime = snapshot.child("endTime").getValue(Long.class);
                Boolean breakState = snapshot.child("isBreak").getValue(Boolean.class);

                if (running == null) running = false;
                if (endTime == null) endTime = 0L;
                if (breakState == null) breakState = false;

                isBreak = breakState;

                if (running) {
                    startLocalTimer(endTime);
                    tvStatus.setText(isBreak ? "Break Time" : "Focus Time");
                } else {
                    if (countDownTimer != null) countDownTimer.cancel();
                    tvStatus.setText("Paused");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // ===============================
    // LOCAL COUNTDOWN
    // ===============================

    private void startLocalTimer(long endTime) {

        if (countDownTimer != null) countDownTimer.cancel();

        remainingMillis = endTime - System.currentTimeMillis();

        if (remainingMillis <= 0) {
            switchSession();
            return;
        }

        countDownTimer = new CountDownTimer(remainingMillis, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {
                remainingMillis = millisUntilFinished;
                updateTimerUI(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                remainingMillis = 0;
                updateTimerUI(0);
                switchSession();
            }
        }.start();
    }

    // ===============================
    // SWITCH WORK <-> BREAK
    // ===============================

    private void switchSession() {

        if (!isHost) return;

        isBreak = !isBreak;

        timerRef.child("isBreak").setValue(isBreak);
        timerRef.child("running").setValue(false);

        remainingMillis = (isBreak ? breakDuration : workDuration) * 60L * 1000L;

        Toast.makeText(this,
                isBreak ? "Break Started!" : "Focus Time!",
                Toast.LENGTH_LONG).show();
    }

    // ===============================
    // UI UPDATE
    // ===============================

    private void updateTimerUI(long millis) {
        int minutes = (int) (millis / 1000) / 60;
        int seconds = (int) (millis / 1000) % 60;

        tvTimer.setText(String.format("%02d:%02d", minutes, seconds));
        tvTimerType.setText(isBreak ?
                "Break (" + breakDuration + " min)" :
                "Work (" + workDuration + " min)");
    }

    // ===============================
    // LOAD SETTINGS
    // ===============================

    private void loadTimerSettings() {
        DatabaseReference settingsRef = FirebaseDatabase.getInstance()
                .getReference("Rooms")
                .child(roomCode)
                .child("timerSettings");

        settingsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Integer work = snapshot.child("workDuration").getValue(Integer.class);
                Integer breakTime = snapshot.child("breakDuration").getValue(Integer.class);

                if (work != null) workDuration = work;
                if (breakTime != null) breakDuration = breakTime;

                remainingMillis = workDuration * 60L * 1000L;
                updateTimerUI(remainingMillis);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // ===============================
    // SETTINGS DIALOG
    // ===============================

    private void showSettingsDialog() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_timer_settings, null);

        EditText etWork = view.findViewById(R.id.etWorkDuration);
        EditText etBreak = view.findViewById(R.id.etBreakDuration);

        etWork.setText(String.valueOf(workDuration));
        etBreak.setText(String.valueOf(breakDuration));

        builder.setView(view)
                .setPositiveButton("Save", (dialog, which) -> {

                    int newWork = Integer.parseInt(etWork.getText().toString());
                    int newBreak = Integer.parseInt(etBreak.getText().toString());

                    workDuration = newWork;
                    breakDuration = newBreak;

                    FirebaseDatabase.getInstance()
                            .getReference("Rooms")
                            .child(roomCode)
                            .child("timerSettings")
                            .child("workDuration")
                            .setValue(newWork);

                    FirebaseDatabase.getInstance()
                            .getReference("Rooms")
                            .child(roomCode)
                            .child("timerSettings")
                            .child("breakDuration")
                            .setValue(newBreak);

                    remainingMillis = workDuration * 60L * 1000L;
                    updateTimerUI(remainingMillis);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}