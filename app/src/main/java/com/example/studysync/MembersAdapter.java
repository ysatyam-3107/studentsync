package com.example.studysync;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

        // Tap anywhere on item → open profile dialog (shows Remove button if host)
        holder.itemView.setOnClickListener(v -> showProfileDialog(member));
        holder.ivAvatar.setOnClickListener(v -> showProfileDialog(member));
    }

    @Override
    public int getItemCount() {
        return members == null ? 0 : members.size();
    }

    /**
     * Shows member profile photo + name.
     * If current user is host AND this member is not themselves → shows "Remove" button.
     */
    private void showProfileDialog(Member member) {
        View dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_profile_photo, null);

        ImageView ivFullPhoto   = dialogView.findViewById(R.id.ivFullProfilePhoto);
        TextView  tvFullName    = dialogView.findViewById(R.id.tvFullProfileName);
        Button    btnRemove     = dialogView.findViewById(R.id.btnRemoveMember);

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

        AlertDialog dialog = new AlertDialog.Builder(context, R.style.DarkDialogTheme)
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .create();

        // Show Remove button only if viewer is host AND this is NOT their own profile
        String currentUid = "";
        if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        boolean isSelf = currentUid.equals(member.getUid());

        if (isHost && !isSelf) {
            btnRemove.setVisibility(View.VISIBLE);
            btnRemove.setOnClickListener(v -> {
                dialog.dismiss();
                showRemoveConfirmDialog(member);
            });
        } else {
            btnRemove.setVisibility(View.GONE);
        }

        dialog.show();
    }

    private void showRemoveConfirmDialog(Member member) {
        String uid = (member.getUid() != null) ? member.getUid() : "";

        new AlertDialog.Builder(context, R.style.DarkDialogTheme)
                .setTitle("Remove Member")
                .setMessage("Remove \"" + member.getName() + "\" from the room?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    if (removeListener != null && !uid.isEmpty()) {
                        removeListener.onRemove(uid);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

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