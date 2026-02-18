package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

public class MainActivity extends AppCompatActivity {

    private CardView cardStudyRoom;
    private TextView tvWelcome, tvRoomCount;
    private ImageView ivProfile;

    // Bottom Navigation
    private ImageButton btnNavHome, btnNavRooms, btnNavTasks, btnNavProfile;

    private FirebaseAuth auth;
    private DatabaseReference userRef, roomsRef;

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

        // Initialize views
        tvWelcome = findViewById(R.id.tvWelcome);
        tvRoomCount = findViewById(R.id.tvRoomCount);
        cardStudyRoom = findViewById(R.id.cardStudyRoom);
        ivProfile = findViewById(R.id.ivProfile);

        btnNavHome = findViewById(R.id.btnNavHome);
        btnNavRooms = findViewById(R.id.btnNavRooms);
        btnNavTasks = findViewById(R.id.btnNavTasks);
        btnNavProfile = findViewById(R.id.btnNavProfile);

        // Load data
        loadUserName(user.getUid());
        loadRoomCount(user.getUid());

        animateEntrance();

        // Study Room Click
        cardStudyRoom.setOnClickListener(v -> {
            animateButton(cardStudyRoom);
            cardStudyRoom.postDelayed(() ->
                    startActivity(new Intent(this, StudyRoomActivity.class)), 200);
        });

        // Profile Click
        ivProfile.setOnClickListener(v -> {
            animateButton(ivProfile);
            ivProfile.postDelayed(() ->
                    startActivity(new Intent(this, ProfileActivity.class)), 200);
        });

        // Bottom Nav
        btnNavHome.setOnClickListener(v ->
                Toast.makeText(this, "You're on Home", Toast.LENGTH_SHORT).show());

        btnNavRooms.setOnClickListener(v -> {
            animateNavButton(btnNavRooms);
            startActivity(new Intent(this, MyRoomsActivity.class));
        });

        btnNavTasks.setOnClickListener(v -> {
            animateNavButton(btnNavTasks);
            startActivity(new Intent(this, TaskActivity.class));
        });

        btnNavProfile.setOnClickListener(v -> {
            animateNavButton(btnNavProfile);
            startActivity(new Intent(this, ProfileActivity.class));
        });

        setActiveNav(btnNavHome);
    }

    private void animateEntrance() {
        cardStudyRoom.setAlpha(0f);
        cardStudyRoom.setTranslationY(100f);

        cardStudyRoom.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setStartDelay(200)
                .start();
    }

    private void animateButton(View view) {
        ObjectAnimator scaleX =
                ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f, 1f);
        ObjectAnimator scaleY =
                ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f, 1f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(200);
        set.start();
    }

    private void animateNavButton(View view) {
        ObjectAnimator scaleX =
                ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.2f, 1f);
        ObjectAnimator scaleY =
                ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.2f, 1f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(150);
        set.setInterpolator(new OvershootInterpolator());
        set.start();
    }

    private void setActiveNav(ImageButton btn) {
        btnNavHome.setAlpha(0.5f);
        btnNavRooms.setAlpha(0.5f);
        btnNavTasks.setAlpha(0.5f);
        btnNavProfile.setAlpha(0.5f);
        btn.setAlpha(1f);
    }

    private void loadUserName(String uid) {
        userRef = FirebaseDatabase.getInstance()
                .getReference("Users").child(uid);

        userRef.addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot s) {
                        String name =
                                s.child("name").getValue(String.class);
                        tvWelcome.setText(
                                name != null ?
                                        "Welcome back, " + name + "!" :
                                        "Welcome back!");
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    private void loadRoomCount(String uid) {
        roomsRef = FirebaseDatabase.getInstance()
                .getReference("Rooms");

        roomsRef.addValueEventListener(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        int count = 0;
                        for (DataSnapshot room : snap.getChildren()) {
                            if (room.child("members").hasChild(uid))
                                count++;
                        }
                        tvRoomCount.setText(
                                count + " Active Room" +
                                        (count != 1 ? "s" : ""));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {}
                });
    }
}
