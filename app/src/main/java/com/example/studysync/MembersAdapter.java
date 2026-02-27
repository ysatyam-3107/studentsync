package com.example.studysync;

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

import java.util.List;

public class MembersAdapter extends RecyclerView.Adapter<MembersAdapter.MemberViewHolder> {

    private final Context context;
    private List<Member> membersList;

    public MembersAdapter(Context context, List<Member> membersList) {
        this.context = context;
        this.membersList = membersList;
    }

    public void updateList(List<Member> newList) {
        this.membersList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_member, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        Member member = membersList.get(position);

        // Show only first name to keep it compact
        String displayName = member.getName();
        if (displayName != null && displayName.contains(" ")) {
            displayName = displayName.substring(0, displayName.indexOf(" "));
        }
        holder.tvName.setText(displayName);

        // Load profile photo with circular crop
        if (member.getPhotoUrl() != null && !member.getPhotoUrl().isEmpty()) {
            Glide.with(context)
                    .load(member.getPhotoUrl())
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.ic_profile_circle)
                    .error(R.drawable.ic_profile_circle)
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_profile_circle);
        }
    }

    @Override
    public int getItemCount() {
        return membersList != null ? membersList.size() : 0;
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName;

        MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivMemberAvatar);
            tvName = itemView.findViewById(R.id.tvMemberName);
        }
    }
}