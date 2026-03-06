package com.example.studysync;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    public interface OnDeleteMessageListener {
        void onDelete(ChatMessage message);
    }

    private final Context context;
    private final List<ChatMessage> messages;
    private final String currentUserId;
    private final OnDeleteMessageListener deleteListener;

    public ChatAdapter(Context context, List<ChatMessage> messages,
                       OnDeleteMessageListener deleteListener) {
        this.context        = context;
        this.messages       = messages;
        this.deleteListener = deleteListener;
        this.currentUserId  = Objects.requireNonNull(
                FirebaseAuth.getInstance().getCurrentUser()).getUid();
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_chat, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);

        holder.tvSenderName.setText(msg.getSenderName());
        holder.tvMessage.setText(msg.getText());

        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        holder.tvTime.setText(sdf.format(new Date(msg.getTimestamp())));

        // ── FIX: Remove blue background — use transparent so item_chat.xml controls styling
        holder.itemView.setBackgroundColor(
                context.getResources().getColor(android.R.color.transparent));

        // ── Long-press → delete option (only for own messages) ───────────────
        boolean isOwn = currentUserId.equals(msg.getSenderId());

        holder.itemView.setOnLongClickListener(v -> {
            if (!isOwn) {
                Toast.makeText(context, "You can only delete your own messages",
                        Toast.LENGTH_SHORT).show();
                return true;
            }
            showDeleteDialog(msg);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    private void showDeleteDialog(ChatMessage msg) {
        new AlertDialog.Builder(context)
                .setTitle("Delete Message")
                .setMessage("Delete this message for everyone?")
                .setPositiveButton("Delete for Everyone", (dialog, which) -> {
                    if (deleteListener != null) deleteListener.onDelete(msg);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView tvSenderName, tvMessage, tvTime;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            tvMessage    = itemView.findViewById(R.id.tvMessage);
            tvTime       = itemView.findViewById(R.id.tvTime);
        }
    }
}