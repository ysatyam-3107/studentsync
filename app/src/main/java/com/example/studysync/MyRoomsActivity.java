package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class MyRoomsActivity extends AppCompatActivity {

    private RecyclerView rvMyRooms;
    private TextView tvNoRooms;
    private MyRoomsAdapter adapter;
    private List<RoomInfo> roomsList;

    private FirebaseAuth auth;
    private DatabaseReference roomsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_rooms);

        rvMyRooms = findViewById(R.id.rvMyRooms);
        tvNoRooms = findViewById(R.id.tvNoRooms);

        auth = FirebaseAuth.getInstance();
        roomsRef = FirebaseDatabase.getInstance().getReference("Rooms");

        roomsList = new ArrayList<>();
        adapter = new MyRoomsAdapter(this, roomsList, new MyRoomsAdapter.OnRoomClickListener() {
            @Override
            public void onRoomClick(RoomInfo room) {
                // Join room
                Intent intent = new Intent(MyRoomsActivity.this, StudyRoomInsideActivity.class);
                intent.putExtra("roomCode", room.getRoomCode());
                startActivity(intent);
            }

            @Override
            public void onDeleteClick(RoomInfo room) {
                // Show delete confirmation
                showDeleteDialog(room);
            }
        });

        rvMyRooms.setLayoutManager(new LinearLayoutManager(this));
        rvMyRooms.setAdapter(adapter);

        loadMyRooms();
    }

    private void loadMyRooms() {
        String userId = auth.getCurrentUser().getUid();

        roomsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                roomsList.clear();

                for (DataSnapshot roomSnap : snapshot.getChildren()) {
                    String roomCode = roomSnap.getKey();
                    DataSnapshot membersSnap = roomSnap.child("members");

                    // Check if user is a member
                    if (membersSnap.hasChild(userId)) {
                        String creatorId = roomSnap.child("createdBy").getValue(String.class);
                        Long timestamp = roomSnap.child("createdAt").getValue(Long.class);

                        int memberCount = (int) membersSnap.getChildrenCount();
                        boolean isHost = userId.equals(creatorId);

                        RoomInfo roomInfo = new RoomInfo(roomCode, memberCount, timestamp, isHost);
                        roomsList.add(roomInfo);
                    }
                }

                adapter.notifyDataSetChanged();

                // Show/hide empty state
                if (roomsList.isEmpty()) {
                    tvNoRooms.setVisibility(android.view.View.VISIBLE);
                    rvMyRooms.setVisibility(android.view.View.GONE);
                } else {
                    tvNoRooms.setVisibility(android.view.View.GONE);
                    rvMyRooms.setVisibility(android.view.View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MyRoomsActivity.this,
                        "Failed to load rooms", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDeleteDialog(RoomInfo room) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Room")
                .setMessage("Are you sure you want to delete room " + room.getRoomCode() + "? This will remove all members and data.")
                .setPositiveButton("Delete", (dialog, which) -> deleteRoom(room.getRoomCode()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteRoom(String roomCode) {
        roomsRef.child(roomCode).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Room deleted successfully", Toast.LENGTH_SHORT).show();

                    // Also delete related data
                    FirebaseDatabase.getInstance().getReference("Messages").child(roomCode).removeValue();
                    FirebaseDatabase.getInstance().getReference("Notes").child(roomCode).removeValue();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to delete room", Toast.LENGTH_SHORT).show());
    }
}