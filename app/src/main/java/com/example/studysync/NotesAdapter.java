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

    private final Context context;
    private final List<Note> notesList;
    private final OnNoteClickListener listener;

    public interface OnNoteClickListener {
        void onDownloadClick(Note note);
        void onDeleteClick(Note note);
    }

    public NotesAdapter(Context context, List<Note> notesList, OnNoteClickListener listener) {
        this.context   = context;
        this.notesList = notesList;
        this.listener  = listener;
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

        // File icon based on type
        String fileType = note.getFileType();
        if (fileType != null) {
            switch (fileType.toLowerCase()) {
                case "pdf":
                    holder.ivFileIcon.setImageResource(android.R.drawable.ic_menu_gallery);
                    break;
                case "text":
                case "document":
                    holder.ivFileIcon.setImageResource(android.R.drawable.ic_menu_edit);
                    break;
                default:
                    holder.ivFileIcon.setImageResource(android.R.drawable.ic_menu_info_details);
            }
        } else {
            holder.ivFileIcon.setImageResource(android.R.drawable.ic_menu_info_details);
        }

        // Show delete button only for the uploader
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        boolean isOwner = note.getUploaderId() != null
                && note.getUploaderId().equals(currentUserId);
        holder.btnDelete.setVisibility(isOwner ? View.VISIBLE : View.GONE);

        // Download — consume click so it doesn't bubble up
        holder.btnDownload.setOnClickListener(v -> {
            v.setPressed(true);
            if (listener != null) listener.onDownloadClick(note);
        });

        // Delete — consume click so it doesn't bubble up or trigger back gesture
        holder.btnDelete.setOnClickListener(v -> {
            v.setPressed(true);
            if (listener != null) listener.onDeleteClick(note);
        });
    }

    @Override
    public int getItemCount() {
        return notesList == null ? 0 : notesList.size();
    }

    public static class NoteViewHolder extends RecyclerView.ViewHolder {
        ImageView   ivFileIcon;
        TextView    tvFileName, tvUploader, tvDate;
        ImageButton btnDownload, btnDelete;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            ivFileIcon  = itemView.findViewById(R.id.ivFileIcon);
            tvFileName  = itemView.findViewById(R.id.tvFileName);
            tvUploader  = itemView.findViewById(R.id.tvUploader);
            tvDate      = itemView.findViewById(R.id.tvDate);
            btnDownload = itemView.findViewById(R.id.btnDownload);
            btnDelete   = itemView.findViewById(R.id.btnDeleteNote);
        }
    }
}