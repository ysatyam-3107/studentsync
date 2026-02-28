package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MyRoomsActivity extends AppCompatActivity {

    private RecyclerView rvMyRooms;
    private View emptyLayout;
    private View headerLayout;
    private FloatingActionButton fabCreateRoom;

    private MyRoomsAdapter adapter;
    private List<RoomInfo> roomsList = new ArrayList<>();

    private FirebaseAuth auth;
    private DatabaseReference roomsRef;
    private ValueEventListener roomsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_rooms);

        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        initViews();
        initRecycler();
        initFirebase();
        loadMyRooms();
        animateHeader();

        // ✅ Persistent navbar — marks "Rooms" tab as active
        BottomNavHelper.setup(this, BottomNavHelper.Tab.ROOMS);
    }

    private void initViews() {
        rvMyRooms    = findViewById(R.id.rvMyRooms);
        emptyLayout  = findViewById(R.id.emptyLayout);
        headerLayout = findViewById(R.id.headerLayout);
        fabCreateRoom = findViewById(R.id.fabCreateRoom);

        fabCreateRoom.setOnClickListener(v ->
                startActivity(new Intent(this, StudyRoomActivity.class)));
    }

    private void initRecycler() {
        adapter = new MyRoomsAdapter(this, roomsList,
                new MyRoomsAdapter.OnRoomClickListener() {
                    @Override public void onRoomClick(RoomInfo room) {
                        Intent intent = new Intent(MyRoomsActivity.this,
                                StudyRoomInsideActivity.class);
                        intent.putExtra("roomCode", room.getRoomCode());
                        startActivity(intent);
                    }
                    @Override public void onDeleteClick(RoomInfo room) {
                        if (room.isHost()) showDeleteDialog(room);
                        else Toast.makeText(MyRoomsActivity.this,
                                "Only host can delete room", Toast.LENGTH_SHORT).show();
                    }
                });

        rvMyRooms.setLayoutManager(new LinearLayoutManager(this));
        rvMyRooms.setAdapter(adapter);
    }

    private void initFirebase() {
        roomsRef = FirebaseDatabase.getInstance().getReference("Rooms");
    }

    private void loadMyRooms() {
        String uid = auth.getCurrentUser().getUid();
        roomsListener = roomsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                roomsList.clear();
                for (DataSnapshot roomSnap : snapshot.getChildren()) {
                    if (!roomSnap.child("members").hasChild(uid)) continue;
                    String  roomCode   = roomSnap.getKey();
                    String  creatorId  = roomSnap.child("createdBy").getValue(String.class);
                    Long    timestamp  = roomSnap.child("createdAt").getValue(Long.class);
                    int     members    = (int) roomSnap.child("members").getChildrenCount();
                    boolean isHost     = uid.equals(creatorId);
                    roomsList.add(new RoomInfo(roomCode, members,
                            timestamp != null ? timestamp : 0, isHost));
                }
                Collections.sort(roomsList,
                        (r1, r2) -> Long.compare(r2.getCreatedAt(), r1.getCreatedAt()));
                adapter.notifyDataSetChanged();
                emptyLayout.setVisibility(roomsList.isEmpty() ? View.VISIBLE : View.GONE);
                rvMyRooms.setVisibility(roomsList.isEmpty() ? View.GONE : View.VISIBLE);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                Toast.makeText(MyRoomsActivity.this, "Failed to load rooms",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDeleteDialog(RoomInfo room) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Room")
                .setMessage("Delete room " + room.getRoomCode() + "?")
                .setPositiveButton("Delete", (d, w) -> deleteRoom(room.getRoomCode()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteRoom(String roomCode) {
        roomsRef.child(roomCode).removeValue().addOnSuccessListener(aVoid -> {
            FirebaseDatabase.getInstance().getReference("Messages").child(roomCode).removeValue();
            FirebaseDatabase.getInstance().getReference("Notes").child(roomCode).removeValue();
            Toast.makeText(this, "Room deleted", Toast.LENGTH_SHORT).show();
        });
    }

    private void animateHeader() {
        if (headerLayout == null) return;
        headerLayout.setTranslationY(-200f);
        ObjectAnimator.ofFloat(headerLayout, "translationY", -200f, 0f)
                .setDuration(700)
                .start();
        headerLayout.animate().translationY(0f)
                .setDuration(700)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomsListener != null) roomsRef.removeEventListener(roomsListener);
    }
}