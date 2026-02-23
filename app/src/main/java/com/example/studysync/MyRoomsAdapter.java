package com.example.studysync;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MyRoomsAdapter
        extends RecyclerView.Adapter<MyRoomsAdapter.RoomViewHolder> {

    private Context context;
    private List<RoomInfo> roomsList;
    private OnRoomClickListener listener;

    public interface OnRoomClickListener {
        void onRoomClick(RoomInfo room);
        void onDeleteClick(RoomInfo room);
    }

    public MyRoomsAdapter(Context context,
                          List<RoomInfo> roomsList,
                          OnRoomClickListener listener) {
        this.context = context;
        this.roomsList = roomsList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RoomViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_room, parent, false);

        return new RoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull RoomViewHolder holder,
            int position) {

        RoomInfo room = roomsList.get(position);

        holder.tvRoomCode.setText("Room: " + room.getRoomCode());

        holder.tvMemberCount.setText(
                "👥 " + room.getMemberCount() + " members");

        // Safe timestamp formatting
        if (room.getCreatedAt() != 0) {
            SimpleDateFormat sdf =
                    new SimpleDateFormat("MMM dd, yyyy",
                            Locale.getDefault());

            holder.tvCreatedDate.setText(
                    "Created: " +
                            sdf.format(
                                    new Date(room.getCreatedAt())
                            )
            );
        } else {
            holder.tvCreatedDate.setText("Created: --");
        }

        // Host badge
        if (room.isHost()) {
            holder.tvHostBadge.setVisibility(View.VISIBLE);
            holder.btnDeleteRoom.setVisibility(View.VISIBLE);
        } else {
            holder.tvHostBadge.setVisibility(View.GONE);
            holder.btnDeleteRoom.setVisibility(View.GONE);
        }

        // Click events
        holder.cardView.setOnClickListener(v -> {
            if (listener != null)
                listener.onRoomClick(room);
        });

        holder.btnDeleteRoom.setOnClickListener(v -> {
            if (listener != null)
                listener.onDeleteClick(room);
        });

        // Smooth fade-in animation
        Animation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(300);
        holder.itemView.startAnimation(fadeIn);
    }

    @Override
    public int getItemCount() {
        return roomsList.size();
    }

    // ===========================
    // ViewHolder
    // ===========================

    public static class RoomViewHolder
            extends RecyclerView.ViewHolder {

        TextView tvRoomCode;
        TextView tvMemberCount;
        TextView tvCreatedDate;
        TextView tvHostBadge;
        Button btnDeleteRoom;
        CardView cardView;

        public RoomViewHolder(@NonNull View itemView) {
            super(itemView);

            cardView = itemView.findViewById(R.id.cardRoom);

            tvRoomCode = itemView.findViewById(R.id.tvRoomCode);
            tvMemberCount = itemView.findViewById(R.id.tvMemberCount);
            tvCreatedDate = itemView.findViewById(R.id.tvCreatedDate);
            tvHostBadge = itemView.findViewById(R.id.tvHostBadge);

            btnDeleteRoom = itemView.findViewById(R.id.btnDeleteRoom);
        }
    }
}