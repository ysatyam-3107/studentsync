package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    private Button btnStudyRoom;
    private TextView tvWelcome, tvRoomCount;

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
        btnStudyRoom = findViewById(R.id.btnStudyRoom);

        // Bottom Navigation
        btnNavHome = findViewById(R.id.btnNavHome);
        btnNavRooms = findViewById(R.id.btnNavRooms);
        btnNavTasks = findViewById(R.id.btnNavTasks);
        btnNavProfile = findViewById(R.id.btnNavProfile);

        // Load user data
        loadUserName(user.getUid());
        loadRoomCount(user.getUid());

        // Main button
        btnStudyRoom.setOnClickListener(v -> {
            startActivity(new Intent(this, StudyRoomActivity.class));
        });

        // Bottom Navigation Listeners
        btnNavHome.setOnClickListener(v -> {
            // Already on home
            Toast.makeText(this, "You're on Home", Toast.LENGTH_SHORT).show();
        });

        btnNavRooms.setOnClickListener(v -> {
            startActivity(new Intent(this, MyRoomsActivity.class));
        });

        btnNavTasks.setOnClickListener(v -> {
            startActivity(new Intent(this, TaskActivity.class));
        });

        btnNavProfile.setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
        });

        // Set home as active
        setActiveNav(btnNavHome);
    }

    private void setActiveNav(ImageButton activeButton) {
        // Reset all
        btnNavHome.setAlpha(0.5f);
        btnNavRooms.setAlpha(0.5f);
        btnNavTasks.setAlpha(0.5f);
        btnNavProfile.setAlpha(0.5f);

        // Set active
        activeButton.setAlpha(1.0f);
    }

    private void loadUserName(String uid) {
        userRef = FirebaseDatabase.getInstance().getReference("Users").child(uid);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    if (name != null && !name.isEmpty()) {
                        tvWelcome.setText("Welcome, " + name + "!");
                    } else {
                        tvWelcome.setText("Welcome to StudySync!");
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvWelcome.setText("Welcome to StudySync!");
            }
        });
    }

    private void loadRoomCount(String uid) {
        roomsRef = FirebaseDatabase.getInstance().getReference("Rooms");

        roomsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int roomCount = 0;

                for (DataSnapshot roomSnap : snapshot.getChildren()) {
                    DataSnapshot membersSnap = roomSnap.child("members");
                    if (membersSnap.hasChild(uid)) {
                        roomCount++;
                    }
                }

                tvRoomCount.setText("📚 " + roomCount + " Active Room" + (roomCount != 1 ? "s" : ""));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvRoomCount.setText("📚 0 Active Rooms");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (auth.getCurrentUser() != null) {
            loadRoomCount(auth.getCurrentUser().getUid());
        }
        setActiveNav(btnNavHome);
    }
}