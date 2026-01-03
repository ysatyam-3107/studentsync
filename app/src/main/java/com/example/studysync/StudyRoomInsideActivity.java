package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class StudyRoomInsideActivity extends AppCompatActivity {

    private static final String TAG = "StudyRoomInsideActivity";

    private TextView tvRoomCode;
    private Button btnVideoCall, btnPomodoroRoom, btnChatRoom, btnNotesRoom, btnTasksRoom, btnLeaveRoom;

    private RecyclerView rvMembers;
    private MembersAdapter membersAdapter;
    private List<Member> membersList;

    private String roomCode;
    private FirebaseAuth auth;
    private DatabaseReference roomRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_room_inside);

        Log.d(TAG, "onCreate called");

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();

        // Check if user is logged in
        if (auth.getCurrentUser() == null) {
            Log.e(TAG, "User not logged in");
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        // Get room code from intent
        roomCode = getIntent().getStringExtra("roomCode");
        Log.d(TAG, "Room code received: " + roomCode);

        if (roomCode == null || roomCode.isEmpty()) {
            Log.e(TAG, "Room code is null or empty");
            Toast.makeText(this, "Invalid room code", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize UI elements
        tvRoomCode = findViewById(R.id.tvRoomCode);
        btnVideoCall = findViewById(R.id.btnVideoCall);
        btnPomodoroRoom = findViewById(R.id.btnPomodoroRoom);
        btnChatRoom = findViewById(R.id.btnChatRoom);
        btnNotesRoom = findViewById(R.id.btnNotesRoom);
        btnTasksRoom = findViewById(R.id.btnTasksRoom);
        btnLeaveRoom = findViewById(R.id.btnLeaveRoom);
        rvMembers = findViewById(R.id.rvMembers);

        // Setup RecyclerView
        rvMembers.setLayoutManager(new LinearLayoutManager(this));
        membersList = new ArrayList<>();
        membersAdapter = new MembersAdapter(this, membersList);
        rvMembers.setAdapter(membersAdapter);

        // Display room code
        tvRoomCode.setText("Room Code: " + roomCode);

        // Firebase reference
        roomRef = FirebaseDatabase.getInstance().getReference("Rooms").child(roomCode);

        // Verify room exists
        verifyRoomExists();

        // Load members
        loadMembers();

        // Set click listeners
        btnPomodoroRoom.setOnClickListener(v -> {
            Intent intent = new Intent(this, PomodoroActivity.class);
            intent.putExtra("roomCode", roomCode);
            startActivity(intent);
        });

        btnChatRoom.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra("roomCode", roomCode);
            startActivity(intent);
        });

        btnNotesRoom.setOnClickListener(v -> {
            Intent intent = new Intent(this, NotesActivity.class);
            intent.putExtra("roomCode", roomCode);
            startActivity(intent);
        });

        btnTasksRoom.setOnClickListener(v -> {
            Intent intent = new Intent(this, TaskActivity.class);
            startActivity(intent);
        });
        btnTasksRoom.setOnClickListener(v -> {
            Intent intent = new Intent(this, TaskActivity.class);
            startActivity(intent);
        });

// ADD THIS - Video Call Button
        btnVideoCall.setOnClickListener(v -> {
            String userId = auth.getCurrentUser().getUid();
            DatabaseReference userRef = FirebaseDatabase.getInstance()
                    .getReference("Users").child(userId);

            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String userName = "User";
                    if (snapshot.exists()) {
                        String name = snapshot.child("name").getValue(String.class);
                        if (name != null && !name.isEmpty()) {
                            userName = name;
                        }
                    }

                    Intent intent = new Intent(StudyRoomInsideActivity.this, VideoCallActivity.class);
                    intent.putExtra("roomCode", roomCode);
                    intent.putExtra("userName", userName);
                    startActivity(intent);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(StudyRoomInsideActivity.this,
                            "Failed to load user data", Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnLeaveRoom.setOnClickListener(v -> leaveRoom());


        btnLeaveRoom.setOnClickListener(v -> leaveRoom());
    }

    private void verifyRoomExists() {
        roomRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.e(TAG, "Room does not exist");
                    Toast.makeText(StudyRoomInsideActivity.this,
                            "This room no longer exists", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Log.d(TAG, "Room verified successfully");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error verifying room: " + error.getMessage());
            }
        });
    }

    private void loadMembers() {
        roomRef.child("members").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                membersList.clear();

                long total = snapshot.getChildrenCount();
                Log.d(TAG, "Loading " + total + " members");

                if (total == 0) {
                    membersAdapter.updateList(membersList);
                    return;
                }

                final long[] processed = {0};

                for (DataSnapshot memberSnap : snapshot.getChildren()) {
                    String uid = memberSnap.getKey();

                    DatabaseReference userRef = FirebaseDatabase.getInstance()
                            .getReference("Users").child(uid);

                    userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot userSnap) {
                            String name = userSnap.child("name").getValue(String.class);
                            String email = userSnap.child("email").getValue(String.class);
                            String photo = userSnap.child("photoUrl").getValue(String.class);

                            String displayName = (name != null && !name.isEmpty()) ? name : uid;
                            String displayEmail = (email != null && !email.isEmpty()) ? email : "";

                            Member m = new Member(uid, displayName, displayEmail, photo);
                            membersList.add(m);

                            processed[0]++;
                            if (processed[0] == total) {
                                Log.d(TAG, "All members loaded: " + membersList.size());
                                membersAdapter.updateList(new ArrayList<>(membersList));
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "Error loading user: " + error.getMessage());
                            processed[0]++;
                            if (processed[0] == total) {
                                membersAdapter.updateList(new ArrayList<>(membersList));
                            }
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading members: " + error.getMessage());
                Toast.makeText(StudyRoomInsideActivity.this,
                        "Failed to load members", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void leaveRoom() {
        if (auth.getCurrentUser() == null) return;

        String uid = auth.getCurrentUser().getUid();

        roomRef.child("members").child(uid).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "You left the room", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to leave room", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called - Room code: " + roomCode);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
    }
}