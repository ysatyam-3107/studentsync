package com.example.studysync;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MembersAdapter extends RecyclerView.Adapter<MembersAdapter.MemberViewHolder> {

    private Context context;
    private List<Member> membersList;

    public MembersAdapter(Context context, List<Member> membersList) {
        this.context = context;
        this.membersList = membersList;
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_member, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        Member member = membersList.get(position);

        // Display name (not UID)
        String displayName = member.getName();
        if (displayName == null || displayName.isEmpty()) {
            displayName = "User"; // Fallback
        }
        holder.tvMemberName.setText(displayName);

        // Display email
        String email = member.getEmail();
        if (email != null && !email.isEmpty()) {
            holder.tvMemberEmail.setText(email);
            holder.tvMemberEmail.setVisibility(View.VISIBLE);
        } else {
            holder.tvMemberEmail.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return membersList.size();
    }

    public void updateList(List<Member> newList) {
        membersList.clear();
        if (newList != null) {
            membersList.addAll(newList);
        }
        notifyDataSetChanged();
    }

    public static class MemberViewHolder extends RecyclerView.ViewHolder {
        TextView tvMemberName, tvMemberEmail;

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMemberName = itemView.findViewById(R.id.tvMemberName);
            tvMemberEmail = itemView.findViewById(R.id.tvMemberEmail);
        }
    }
}