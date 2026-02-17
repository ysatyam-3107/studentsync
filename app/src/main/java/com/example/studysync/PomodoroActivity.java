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

    private CountDownTimer timer;

    private String roomCode;
    private DatabaseReference timerRef;

    private boolean isHost = false;

    // Default timer settings (in minutes)
    private int workDuration = 25;
    private int breakDuration = 5;

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

        if (roomCode == null || roomCode.isEmpty()) {
            Toast.makeText(this, "Invalid room code", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        timerRef = FirebaseDatabase.getInstance().getReference("Rooms")
                .child(roomCode).child("timer");

        checkHost();
        listenForTimer();
        loadTimerSettings();

        btnStart.setOnClickListener(v -> startTimerInFirebase());
        btnStop.setOnClickListener(v -> stopTimerInFirebase());
        btnReset.setOnClickListener(v -> resetTimerInFirebase());
        btnSettings.setOnClickListener(v -> showSettingsDialog());
    }

    private void checkHost() {
        DatabaseReference roomRef = FirebaseDatabase.getInstance()
                .getReference("Rooms").child(roomCode);

        roomRef.child("createdBy").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String creator = snapshot.getValue(String.class);

                if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                    finish();
                    return;
                }

                String myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                isHost = myUid.equals(creator);

                btnStart.setEnabled(isHost);
                btnStop.setEnabled(isHost);
                btnReset.setEnabled(isHost);
                btnSettings.setEnabled(isHost);

                if (!isHost) {
                    tvStatus.setText("Host controls the timer");
                    btnSettings.setVisibility(View.GONE);
                } else {
                    btnSettings.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(PomodoroActivity.this,
                        "Failed to check host status", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadTimerSettings() {
        DatabaseReference settingsRef = FirebaseDatabase.getInstance()
                .getReference("Rooms").child(roomCode).child("timerSettings");

        settingsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Integer work = snapshot.child("workDuration").getValue(Integer.class);
                    Integer breakTime = snapshot.child("breakDuration").getValue(Integer.class);

                    if (work != null) workDuration = work;
                    if (breakTime != null) breakDuration = breakTime;

                    updateTimerDisplay();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_timer_settings, null);

        EditText etWorkDuration = dialogView.findViewById(R.id.etWorkDuration);
        EditText etBreakDuration = dialogView.findViewById(R.id.etBreakDuration);
        Button btnSave = dialogView.findViewById(R.id.btnSaveSettings);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelSettings);

        // Set current values
        etWorkDuration.setText(String.valueOf(workDuration));
        etBreakDuration.setText(String.valueOf(breakDuration));

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        btnSave.setOnClickListener(v -> {
            String workStr = etWorkDuration.getText().toString().trim();
            String breakStr = etBreakDuration.getText().toString().trim();

            if (workStr.isEmpty() || breakStr.isEmpty()) {
                Toast.makeText(this, "Please enter both durations", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                int work = Integer.parseInt(workStr);
                int breakTime = Integer.parseInt(breakStr);

                if (work < 1 || work > 120) {
                    Toast.makeText(this, "Work time must be 1-120 minutes", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (breakTime < 1 || breakTime > 60) {
                    Toast.makeText(this, "Break time must be 1-60 minutes", Toast.LENGTH_SHORT).show();
                    return;
                }

                saveTimerSettings(work, breakTime);
                dialog.dismiss();

            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter valid numbers", Toast.LENGTH_SHORT).show();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void saveTimerSettings(int work, int breakTime) {
        DatabaseReference settingsRef = FirebaseDatabase.getInstance()
                .getReference("Rooms").child(roomCode).child("timerSettings");

        settingsRef.child("workDuration").setValue(work);
        settingsRef.child("breakDuration").setValue(breakTime);

        workDuration = work;
        breakDuration = breakTime;

        Toast.makeText(this, "Timer settings updated!", Toast.LENGTH_SHORT).show();
        updateTimerDisplay();
    }

    private void updateTimerDisplay() {
        int minutes = workDuration;
        tvTimer.setText(String.format("%02d:00", minutes));
        tvTimerType.setText("Work Session (" + workDuration + " min)");
    }

    private void startTimerInFirebase() {
        timerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean isBreak = snapshot.child("isBreak").getValue(Boolean.class);
                if (isBreak == null) isBreak = false;

                int duration = isBreak ? breakDuration : workDuration;
                long endTime = System.currentTimeMillis() + (duration * 60 * 1000);

                timerRef.child("endTime").setValue(endTime);
                timerRef.child("running").setValue(true);
                timerRef.child("isBreak").setValue(isBreak);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void stopTimerInFirebase() {
        timerRef.child("running").setValue(false);
    }

    private void resetTimerInFirebase() {
        timerRef.child("running").setValue(false);
        timerRef.child("endTime").setValue(0);
        timerRef.child("isBreak").setValue(false);
        updateTimerDisplay();
    }

    private void listenForTimer() {
        timerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean running = snapshot.child("running").getValue(Boolean.class);
                Long endTime = snapshot.child("endTime").getValue(Long.class);
                Boolean isBreak = snapshot.child("isBreak").getValue(Boolean.class);

                if (running == null) running = false;
                if (endTime == null) endTime = 0L;
                if (isBreak == null) isBreak = false;

                if (running) {
                    startLocalCountdown(endTime, isBreak);
                    tvStatus.setText(isBreak ? "Break Time!" : "Focus Time!");
                    String sessionType = isBreak ?
                            "Break (" + breakDuration + " min)" :
                            "Work Session (" + workDuration + " min)";
                    tvTimerType.setText(sessionType);
                } else {
                    if (timer != null) timer.cancel();
                    tvStatus.setText("Ready");
                    updateTimerDisplay();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(PomodoroActivity.this,
                        "Error listening to timer", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startLocalCountdown(long endTime, boolean isBreakTime) {
        if (timer != null) timer.cancel();

        long remaining = endTime - System.currentTimeMillis();

        if (remaining <= 0) {
            tvTimer.setText("00:00");
            tvStatus.setText("Session finished!");

            // Auto-switch to break/work
            if (isHost) {
                timerRef.child("isBreak").setValue(!isBreakTime);
                timerRef.child("running").setValue(false);
            }
            return;
        }

        timer = new CountDownTimer(remaining, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int min = (int) (millisUntilFinished / 1000) / 60;
                int sec = (int) (millisUntilFinished / 1000) % 60;
                tvTimer.setText(String.format("%02d:%02d", min, sec));
            }

            @Override
            public void onFinish() {
                tvTimer.setText("00:00");
                tvStatus.setText(isBreakTime ? "Break finished!" : "Focus session finished!");
                Toast.makeText(PomodoroActivity.this,
                        isBreakTime ? "Time to work!" : "Take a break!",
                        Toast.LENGTH_LONG).show();

                // Auto-switch between work and break
                if (isHost) {
                    timerRef.child("isBreak").setValue(!isBreakTime);
                    timerRef.child("running").setValue(false);
                }
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
    }
}