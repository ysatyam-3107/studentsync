package com.example.studysync;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.NoteViewHolder> {

    private Context context;
    private List<Note> notesList;
    private OnNoteClickListener listener;

    public interface OnNoteClickListener {
        void onDownloadClick(Note note);
        void onDeleteClick(Note note);
    }

    public NotesAdapter(Context context, List<Note> notesList, OnNoteClickListener listener) {
        this.context = context;
        this.notesList = notesList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note note = notesList.get(position);

        holder.tvFileName.setText(note.getFileName());
        holder.tvUploader.setText("Uploaded by: " + note.getUploaderName());

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        holder.tvDate.setText(sdf.format(new Date(note.getUploadedAt())));

        // ✅ FIXED: Add null check for fileType
        String fileType = note.getFileType();
        if (fileType != null) {
            // Set file icon based on type
            if (fileType.equals("pdf")) {
                holder.ivFileIcon.setImageResource(android.R.drawable.ic_menu_gallery);
            } else if (fileType.equals("text")) {
                holder.ivFileIcon.setImageResource(android.R.drawable.ic_menu_edit);
            } else if (fileType.equals("document")) {
                holder.ivFileIcon.setImageResource(android.R.drawable.ic_menu_edit);
            } else {
                holder.ivFileIcon.setImageResource(android.R.drawable.ic_menu_info_details);
            }
        } else {
            // Default icon if fileType is null
            holder.ivFileIcon.setImageResource(android.R.drawable.ic_menu_info_details);
        }

        // Show delete button only for uploader
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (note.getUploaderId().equals(currentUserId)) {
            holder.btnDelete.setVisibility(View.VISIBLE);
        } else {
            holder.btnDelete.setVisibility(View.GONE);
        }

        holder.btnDownload.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDownloadClick(note);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(note);
            }
        });
    }

    @Override
    public int getItemCount() {
        return notesList.size();
    }

    public static class NoteViewHolder extends RecyclerView.ViewHolder {
        ImageView ivFileIcon;
        TextView tvFileName, tvUploader, tvDate;
        ImageButton btnDownload, btnDelete;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            ivFileIcon = itemView.findViewById(R.id.ivFileIcon);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvUploader = itemView.findViewById(R.id.tvUploader);
            tvDate = itemView.findViewById(R.id.tvDate);
            btnDownload = itemView.findViewById(R.id.btnDownload);
            btnDelete = itemView.findViewById(R.id.btnDeleteNote);
        }
    }
}