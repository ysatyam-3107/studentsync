package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private TextView tvWelcome, tvRoomCount;
    private ImageView ivProfile;
    private CardView cardStudyRoom;

    private ImageButton btnNavHome, btnNavRooms, btnNavTasks, btnNavProfile;

    private FirebaseAuth auth;
    private DatabaseReference roomsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        initViews();
        setGreeting(user.getUid());
        loadRoomCount(user.getUid());
        setupClickListeners();
        animateEntrance();
    }

    private void initViews() {
        tvWelcome = findViewById(R.id.tvWelcome);
        tvRoomCount = findViewById(R.id.tvRoomCount);
        ivProfile = findViewById(R.id.ivProfile);
        cardStudyRoom = findViewById(R.id.cardStudyRoom);

        btnNavHome = findViewById(R.id.btnNavHome);
        btnNavRooms = findViewById(R.id.btnNavRooms);
        btnNavTasks = findViewById(R.id.btnNavTasks);
        btnNavProfile = findViewById(R.id.btnNavProfile);
    }

    /* ------------------- GREETING SYSTEM ------------------- */

    private void setGreeting(String uid) {

        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("Users").child(uid);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                String name = snapshot.child("name").getValue(String.class);
                if (name == null) name = "User";

                Calendar calendar = Calendar.getInstance();
                int hour = calendar.get(Calendar.HOUR_OF_DAY);

                String greeting;

                if (hour < 12) {
                    greeting = "Good Morning";
                } else if (hour < 17) {
                    greeting = "Good Afternoon";
                } else {
                    greeting = "Good Evening";
                }

                tvWelcome.setText(greeting + ", " + name + " 👋");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    /* ------------------- ROOM COUNT WITH ANIMATION ------------------- */

    private void loadRoomCount(String uid) {

        roomsRef = FirebaseDatabase.getInstance().getReference("Rooms");

        roomsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                int count = 0;

                for (DataSnapshot room : snapshot.getChildren()) {
                    if (room.child("members").hasChild(uid)) {
                        count++;
                    }
                }

                animateRoomCounter(count);

                // Make card clickable to open MyRooms
                cardStudyRoom.setOnClickListener(v -> {
                    startActivity(new Intent(MainActivity.this, MyRoomsActivity.class));
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void animateRoomCounter(int finalCount) {

        ValueAnimator animator = ValueAnimator.ofInt(0, finalCount);
        animator.setDuration(800);

        animator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            tvRoomCount.setText(value + " Active Room" + (value != 1 ? "s" : ""));
        });

        animator.start();
    }

    /* ------------------- CLICK LISTENERS ------------------- */

    private void setupClickListeners() {

        ivProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        btnNavHome.setOnClickListener(v -> {});

        btnNavRooms.setOnClickListener(v ->
                startActivity(new Intent(this, MyRoomsActivity.class)));

        btnNavTasks.setOnClickListener(v ->
                startActivity(new Intent(this, TaskActivity.class)));

        btnNavProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
    }

    /* ------------------- SMOOTH ENTRANCE ANIMATION ------------------- */

    private void animateEntrance() {

        cardStudyRoom.setAlpha(0f);
        cardStudyRoom.setTranslationY(100f);

        cardStudyRoom.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(700)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }
}