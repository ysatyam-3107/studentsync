package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

public class PomodoroActivity extends AppCompatActivity {

    private TextView tvTimer, tvStatus;
    private Button btnStart, btnStop, btnReset;

    private CountDownTimer timer;

    private String roomCode;
    private DatabaseReference timerRef;

    private boolean isHost = false;

    private final int WORK_TIME = 25 * 60 * 1000;  // 25 minutes
    private final int BREAK_TIME = 5 * 60 * 1000;  // 5 minutes

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pomodoro);

        tvTimer = findViewById(R.id.tvTimer);
        tvStatus = findViewById(R.id.tvStatus);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnReset = findViewById(R.id.btnReset);

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

        btnStart.setOnClickListener(v -> startTimerInFirebase());
        btnStop.setOnClickListener(v -> stopTimerInFirebase());
        btnReset.setOnClickListener(v -> resetTimerInFirebase());
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

                if (!isHost) {
                    tvStatus.setText("Waiting for host to control the timer...");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(PomodoroActivity.this,
                        "Failed to check host status", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startTimerInFirebase() {
        timerRef.child("endTime").setValue(System.currentTimeMillis() + WORK_TIME);
        timerRef.child("running").setValue(true);
        timerRef.child("isBreak").setValue(false);
    }

    private void stopTimerInFirebase() {
        timerRef.child("running").setValue(false);
    }

    private void resetTimerInFirebase() {
        timerRef.child("running").setValue(false);
        timerRef.child("endTime").setValue(0);
        timerRef.child("isBreak").setValue(false);
        tvTimer.setText("25:00");
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
                } else {
                    if (timer != null) timer.cancel();
                    tvStatus.setText("Stopped");
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
                        "Timer completed!", Toast.LENGTH_SHORT).show();
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