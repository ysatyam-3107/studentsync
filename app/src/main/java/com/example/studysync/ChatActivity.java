package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView rvChat;
    private EditText etMessage;
    private Button btnSend;

    private ChatAdapter chatAdapter;
    private List<ChatMessage> messagesList;

    private String roomCode;
    private DatabaseReference messagesRef;
    private FirebaseAuth auth;
    private String currentUserName = "User"; // Default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        rvChat = findViewById(R.id.rvChat);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

        roomCode = getIntent().getStringExtra("roomCode");
        auth = FirebaseAuth.getInstance();

        if (roomCode == null || auth.getCurrentUser() == null) {
            Toast.makeText(this, "Invalid session", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        messagesRef = FirebaseDatabase.getInstance()
                .getReference("Messages").child(roomCode);

        messagesList = new ArrayList<>();
        chatAdapter = new ChatAdapter(this, messagesList);

        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(chatAdapter);

        // Load current user's name
        loadCurrentUserName();

        // Load messages
        loadMessages();

        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void loadCurrentUserName() {
        String uid = auth.getCurrentUser().getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("Users").child(uid);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    if (name != null && !name.isEmpty()) {
                        currentUserName = name;
                    } else {
                        // Fallback to email username
                        String email = auth.getCurrentUser().getEmail();
                        if (email != null) {
                            currentUserName = email.split("@")[0];
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Use email as fallback
                String email = auth.getCurrentUser().getEmail();
                if (email != null) {
                    currentUserName = email.split("@")[0];
                }
            }
        });
    }

    private void loadMessages() {
        messagesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                messagesList.clear();
                for (DataSnapshot msgSnap : snapshot.getChildren()) {
                    ChatMessage msg = msgSnap.getValue(ChatMessage.class);
                    if (msg != null) {
                        messagesList.add(msg);
                    }
                }
                chatAdapter.notifyDataSetChanged();
                if (messagesList.size() > 0) {
                    rvChat.smoothScrollToPosition(messagesList.size() - 1);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChatActivity.this,
                        "Failed to load messages", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        String senderId = auth.getCurrentUser().getUid();

        Map<String, Object> message = new HashMap<>();
        message.put("senderId", senderId);
        message.put("senderName", currentUserName); // Use the loaded name
        message.put("text", text);
        message.put("timestamp", ServerValue.TIMESTAMP);

        messagesRef.push().setValue(message)
                .addOnSuccessListener(aVoid -> etMessage.setText(""))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to send", Toast.LENGTH_SHORT).show());
    }
}