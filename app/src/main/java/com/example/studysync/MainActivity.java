package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private TextView tvWelcome, tvRoomCount;
    private ImageView ivProfile;
    private CardView cardStudyRoom;
    private CardView cardActiveRooms; // ✅ Added — was never declared

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
        loadUserPhoto(user.getUid());
        loadRoomCount(user.getUid());
        animateEntrance();

        BottomNavHelper.setup(this, BottomNavHelper.Tab.HOME);
    }

    @Override
    protected void onResume() {
        super.onResume();
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) loadUserPhoto(user.getUid());
    }

    private void initViews() {
        tvWelcome      = findViewById(R.id.tvWelcome);
        tvRoomCount    = findViewById(R.id.tvRoomCount);
        ivProfile      = findViewById(R.id.ivProfile);
        cardStudyRoom  = findViewById(R.id.cardStudyRoom);
        cardActiveRooms = findViewById(R.id.cardActiveRooms); // ✅ Added

        // Profile photo → ProfileActivity
        ivProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        // ✅ Active Rooms card → MyRoomsActivity
        //    This was the bug: the card was clickable in XML but had NO listener in Java
        cardActiveRooms.setOnClickListener(v ->
                startActivity(new Intent(this, MyRoomsActivity.class)));

        // Quick Start card → StudyRoomActivity (create/join)
        cardStudyRoom.setOnClickListener(v ->
                startActivity(new Intent(this, StudyRoomActivity.class)));
    }

    private void loadUserPhoto(String uid) {
        FirebaseDatabase.getInstance()
                .getReference("Users").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String photoUrl = snapshot.child("photoUrl").getValue(String.class);
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            Glide.with(MainActivity.this)
                                    .load(photoUrl)
                                    .transform(new CircleCrop())
                                    .placeholder(R.drawable.ic_profile_circle)
                                    .error(R.drawable.ic_profile_circle)
                                    .into(ivProfile);
                        } else {
                            ivProfile.setImageResource(R.drawable.ic_profile_circle);
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        ivProfile.setImageResource(R.drawable.ic_profile_circle);
                    }
                });
    }

    private void setGreeting(String uid) {
        FirebaseDatabase.getInstance()
                .getReference("Users").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String name = snapshot.child("name").getValue(String.class);
                        if (name == null) name = "User";
                        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                        String g = hour < 12 ? "Good Morning"
                                : hour < 17 ? "Good Afternoon"
                                : "Good Evening";
                        tvWelcome.setText(g + ", " + name + " 👋");
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void loadRoomCount(String uid) {
        roomsRef = FirebaseDatabase.getInstance().getReference("Rooms");
        roomsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int count = 0;
                for (DataSnapshot room : snapshot.getChildren()) {
                    if (room.child("members").hasChild(uid)) count++;
                }
                animateRoomCounter(count);

                // ✅ cardStudyRoom click is set once here in initViews(),
                //    not re-set on every Firebase update (cleaner)
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void animateRoomCounter(int finalCount) {
        ValueAnimator anim = ValueAnimator.ofInt(0, finalCount);
        anim.setDuration(800);
        anim.addUpdateListener(a -> {
            int v = (int) a.getAnimatedValue();
            tvRoomCount.setText(v + " Active Room" + (v != 1 ? "s" : ""));
        });
        anim.start();
    }

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