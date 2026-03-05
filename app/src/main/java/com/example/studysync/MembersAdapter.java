package com.example.studysync;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;

public class MembersAdapter extends RecyclerView.Adapter<MembersAdapter.MemberViewHolder> {

    public interface OnRemoveMemberListener {
        void onRemove(String uid);
    }

    private final Context context;
    private List<Member> members;
    private final boolean isHost;
    private final OnRemoveMemberListener removeListener;

    public MembersAdapter(Context context, List<Member> members,
                          boolean isHost, OnRemoveMemberListener removeListener) {
        this.context        = context;
        this.members        = members;
        this.isHost         = isHost;
        this.removeListener = removeListener;
    }

    public void updateList(List<Member> newList) {
        this.members = newList;
        // FIX: more efficient than notifyDataSetChanged
        notifyItemRangeChanged(0, newList.size());
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_member, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        Member member = members.get(position);

        holder.tvName.setText(member.getName());

        if (member.getPhotoUrl() != null && !member.getPhotoUrl().isEmpty()) {
            Glide.with(context)
                    .load(member.getPhotoUrl())
                    .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                    .placeholder(R.drawable.ic_profile_circle)
                    .error(R.drawable.ic_profile_circle)
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_profile_circle);
        }

        // Tap -> full profile photo dialog (all users)
        holder.ivAvatar.setOnClickListener(v -> showProfilePhotoDialog(member));

        // Long-press -> remove member (host only)
        holder.itemView.setOnLongClickListener(v -> {
            if (isHost) {
                showRemoveMemberDialog(member);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return members == null ? 0 : members.size();
    }

    // ── Profile photo dialog ──────────────────────────────────────────────────

    private void showProfilePhotoDialog(Member member) {
        View dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_profile_photo, null);

        ImageView ivFullPhoto = dialogView.findViewById(R.id.ivFullProfilePhoto);
        TextView  tvFullName  = dialogView.findViewById(R.id.tvFullProfileName);

        tvFullName.setText(member.getName());

        if (member.getPhotoUrl() != null && !member.getPhotoUrl().isEmpty()) {
            Glide.with(context)
                    .load(member.getPhotoUrl())
                    .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                    .placeholder(R.drawable.ic_profile_circle)
                    .error(R.drawable.ic_profile_circle)
                    .into(ivFullPhoto);
        } else {
            ivFullPhoto.setImageResource(R.drawable.ic_profile_circle);
        }

        // FIX: DarkDialogTheme is now properly defined in themes.xml
        new AlertDialog.Builder(context, R.style.DarkDialogTheme)
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .show();
    }

    // ── Remove member dialog (host only) ─────────────────────────────────────

    private void showRemoveMemberDialog(Member member) {
        // FIX: null-safe uid — fixes "getUid() may produce NullPointerException"
        String uid = (member.getUid() != null) ? member.getUid() : "";

        new AlertDialog.Builder(context, R.style.DarkDialogTheme)
                .setTitle("Remove Member")
                .setMessage("Remove " + member.getName() + " from the room?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    if (removeListener != null && !uid.isEmpty()) {
                        removeListener.onRemove(uid);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView  tvName;

        MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivMemberAvatar);
            tvName   = itemView.findViewById(R.id.tvMemberName);
        }
    }
}