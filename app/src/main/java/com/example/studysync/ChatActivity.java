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
    private String currentUserName = "User";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        rvChat    = findViewById(R.id.rvChat);
        etMessage = findViewById(R.id.etMessage);
        btnSend   = findViewById(R.id.btnSend);

        roomCode = getIntent().getStringExtra("roomCode");
        auth     = FirebaseAuth.getInstance();

        if (roomCode == null || auth.getCurrentUser() == null) {
            Toast.makeText(this, "Invalid session", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        messagesRef = FirebaseDatabase.getInstance()
                .getReference("Messages").child(roomCode);

        messagesList = new ArrayList<>();

        // Pass delete callback — removes message from Firebase for everyone
        chatAdapter = new ChatAdapter(this, messagesList, this::deleteMessage);

        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(chatAdapter);

        loadCurrentUserName();
        loadMessages();

        btnSend.setOnClickListener(v -> sendMessage());
    }

    // ─── Load user name ───────────────────────────────────────────────────────

    private void loadCurrentUserName() {
        String uid = auth.getCurrentUser().getUid();
        FirebaseDatabase.getInstance().getReference("Users").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String name = snapshot.child("name").getValue(String.class);
                        if (name != null && !name.isEmpty()) {
                            currentUserName = name;
                        } else {
                            String email = auth.getCurrentUser().getEmail();
                            if (email != null) currentUserName = email.split("@")[0];
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        String email = auth.getCurrentUser().getEmail();
                        if (email != null) currentUserName = email.split("@")[0];
                    }
                });
    }

    // ─── Load messages ────────────────────────────────────────────────────────

    private void loadMessages() {
        messagesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                messagesList.clear();
                for (DataSnapshot msgSnap : snapshot.getChildren()) {
                    ChatMessage msg = msgSnap.getValue(ChatMessage.class);
                    if (msg != null) {
                        msg.setMessageId(msgSnap.getKey()); // store Firebase key for deletion
                        messagesList.add(msg);
                    }
                }
                chatAdapter.notifyDataSetChanged();
                if (!messagesList.isEmpty()) {
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

    // ─── Send message ─────────────────────────────────────────────────────────

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        String senderId = auth.getCurrentUser().getUid();

        Map<String, Object> message = new HashMap<>();
        message.put("senderId",   senderId);
        message.put("senderName", currentUserName);
        message.put("text",       text);
        message.put("timestamp",  ServerValue.TIMESTAMP);

        messagesRef.push().setValue(message)
                .addOnSuccessListener(aVoid -> etMessage.setText(""))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to send", Toast.LENGTH_SHORT).show());
    }

    // ─── Delete message for everyone ──────────────────────────────────────────

    private void deleteMessage(ChatMessage msg) {
        if (msg.getMessageId() == null || msg.getMessageId().isEmpty()) {
            Toast.makeText(this, "Cannot delete this message", Toast.LENGTH_SHORT).show();
            return;
        }
        messagesRef.child(msg.getMessageId()).removeValue()
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show());
    }
}