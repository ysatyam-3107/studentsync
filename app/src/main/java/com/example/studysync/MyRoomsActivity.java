package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class MyRoomsActivity extends AppCompatActivity {

    private RecyclerView rvMyRooms;
    private View emptyLayout;
    private View progressBar;

    private MyRoomsAdapter adapter;
    private List<RoomInfo> roomsList = new ArrayList<>();

    private FirebaseAuth auth;
    private DatabaseReference userRoomsRef;
    private DatabaseReference roomsRef;

    private ValueEventListener roomsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_rooms);

        initViews();
        initFirebase();
        setupRecyclerView();
        loadMyRooms();
    }

    private void initViews() {
        rvMyRooms = findViewById(R.id.rvMyRooms);
        emptyLayout = findViewById(R.id.emptyLayout);
        progressBar = findViewById(R.id.progressBar);
    }

    private void initFirebase() {
        auth = FirebaseAuth.getInstance();
        String uid = auth.getCurrentUser().getUid();

        userRoomsRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(uid)
                .child("rooms");

        roomsRef = FirebaseDatabase.getInstance()
                .getReference("Rooms");
    }

    private void setupRecyclerView() {
        adapter = new MyRoomsAdapter(this, roomsList,
                new MyRoomsAdapter.OnRoomClickListener() {

                    @Override
                    public void onRoomClick(RoomInfo room) {
                        Intent intent = new Intent(
                                MyRoomsActivity.this,
                                StudyRoomInsideActivity.class);
                        intent.putExtra("roomCode", room.getRoomCode());
                        startActivity(intent);
                    }

                    @Override
                    public void onDeleteClick(RoomInfo room) {
                        if (room.isHost()) {
                            showDeleteDialog(room);
                        } else {
                            Toast.makeText(MyRoomsActivity.this,
                                    "Only host can delete room",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        rvMyRooms.setLayoutManager(new LinearLayoutManager(this));
        rvMyRooms.setAdapter(adapter);
    }

    private void loadMyRooms() {

        showLoading(true);

        roomsListener = userRoomsRef.addValueEventListener(
                new ValueEventListener() {

                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {

                        roomsList.clear();

                        if (!snapshot.exists()) {
                            showEmptyState();
                            return;
                        }

                        for (DataSnapshot roomSnap : snapshot.getChildren()) {

                            String roomCode = roomSnap.getKey();

                            roomsRef.child(roomCode)
                                    .addListenerForSingleValueEvent(
                                            new ValueEventListener() {
                                                @Override
                                                public void onDataChange(@NonNull DataSnapshot roomData) {

                                                    if (!roomData.exists()) return;

                                                    String creatorId = roomData.child("createdBy")
                                                            .getValue(String.class);

                                                    Long timestamp = roomData.child("createdAt")
                                                            .getValue(Long.class);

                                                    int memberCount = (int) roomData
                                                            .child("members")
                                                            .getChildrenCount();

                                                    boolean isHost = auth.getCurrentUser()
                                                            .getUid()
                                                            .equals(creatorId);

                                                    RoomInfo room = new RoomInfo(
                                                            roomCode,
                                                            memberCount,
                                                            timestamp,
                                                            isHost
                                                    );

                                                    roomsList.add(room);
                                                    adapter.notifyDataSetChanged();
                                                    showContent();
                                                }

                                                @Override
                                                public void onCancelled(@NonNull DatabaseError error) {
                                                }
                                            });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        showLoading(false);
                        Toast.makeText(MyRoomsActivity.this,
                                "Failed to load rooms",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showDeleteDialog(RoomInfo room) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Room")
                .setMessage("Delete room " + room.getRoomCode() + " permanently?")
                .setPositiveButton("Delete",
                        (dialog, which) -> deleteRoom(room.getRoomCode()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteRoom(String roomCode) {

        roomsRef.child(roomCode).removeValue()
                .addOnSuccessListener(aVoid -> {

                    FirebaseDatabase.getInstance()
                            .getReference("Messages")
                            .child(roomCode)
                            .removeValue();

                    FirebaseDatabase.getInstance()
                            .getReference("Notes")
                            .child(roomCode)
                            .removeValue();

                    Toast.makeText(this,
                            "Room deleted",
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Delete failed",
                                Toast.LENGTH_SHORT).show());
    }

    private void showLoading(boolean loading) {
        if (progressBar != null)
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);

        rvMyRooms.setVisibility(loading ? View.GONE : View.VISIBLE);
        emptyLayout.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        showLoading(false);
        rvMyRooms.setVisibility(View.GONE);
        emptyLayout.setVisibility(View.VISIBLE);
    }

    private void showContent() {
        showLoading(false);
        rvMyRooms.setVisibility(View.VISIBLE);
        emptyLayout.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (roomsListener != null && userRoomsRef != null) {
            userRoomsRef.removeEventListener(roomsListener);
        }
    }
}