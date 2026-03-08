package com.example.studysync;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // ── View types ─────────────────────────────────────────────────────────────
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_TASK   = 1;

    // ── Priority colors ────────────────────────────────────────────────────────
    public static final int COLOR_HIGH   = 0xFFEF5350; // red
    public static final int COLOR_MEDIUM = 0xFFFF9800; // orange
    public static final int COLOR_LOW    = 0xFF4CAF50; // green

    private final Context              context;
    private final List<Object>         items = new ArrayList<>(); // String headers + Task items
    private final OnTaskClickListener  listener;

    public interface OnTaskClickListener {
        void onTaskChecked(Task task, boolean isChecked);
        void onTaskDelete(Task task);
    }

    public TaskAdapter(Context context, OnTaskClickListener listener) {
        this.context  = context;
        this.listener = listener;
    }

    // ── Data update — builds header + task list from raw task list ─────────────
    public void submitList(List<Task> pending, List<Task> done) {
        items.clear();

        if (!pending.isEmpty()) {
            items.add("📋 To Do (" + pending.size() + ")");
            items.addAll(pending);
        }

        if (!done.isEmpty()) {
            items.add("✅ Done (" + done.size() + ")");
            items.addAll(done);
        }

        notifyDataSetChanged();
    }

    // ── Attach swipe-to-action to a RecyclerView ───────────────────────────────
    public void attachSwipeHelper(RecyclerView rv) {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            private final ColorDrawable bgComplete = new ColorDrawable(0xFF388E3C);
            private final ColorDrawable bgDelete   = new ColorDrawable(0xFFB71C1C);

            @Override
            public boolean onMove(@NonNull RecyclerView r,
                                  @NonNull RecyclerView.ViewHolder v,
                                  @NonNull RecyclerView.ViewHolder t) { return false; }

            @Override
            public boolean canDropOver(@NonNull RecyclerView rv,
                                       @NonNull RecyclerView.ViewHolder c,
                                       @NonNull RecyclerView.ViewHolder t) { return false; }

            @Override
            public int getSwipeDirs(@NonNull RecyclerView rv,
                                    @NonNull RecyclerView.ViewHolder vh) {
                // Only allow swipe on task rows, not headers
                return vh instanceof TaskViewHolder
                        ? super.getSwipeDirs(rv, vh)
                        : 0;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
                int pos = vh.getAdapterPosition();
                if (pos == RecyclerView.NO_ID || !(items.get(pos) instanceof Task)) return;
                Task task = (Task) items.get(pos);

                if (dir == ItemTouchHelper.RIGHT) {
                    // Swipe right → toggle complete
                    listener.onTaskChecked(task, !task.isCompleted());
                } else {
                    // Swipe left → delete
                    listener.onTaskDelete(task);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv,
                                    @NonNull RecyclerView.ViewHolder vh,
                                    float dX, float dY, int action, boolean active) {
                View item = vh.itemView;
                Paint paint = new Paint();
                paint.setAntiAlias(true);

                if (dX > 0) {
                    // Swiping right — green "complete" background
                    bgComplete.setBounds(item.getLeft(), item.getTop(),
                            item.getLeft() + (int) dX, item.getBottom());
                    bgComplete.draw(c);
                    // Draw checkmark emoji
                    paint.setTextSize(52f);
                    c.drawText("✓", item.getLeft() + 32f,
                            item.getTop() + (item.getHeight() / 2f) + 18f, paint);
                } else if (dX < 0) {
                    // Swiping left — red "delete" background
                    bgDelete.setBounds(item.getRight() + (int) dX, item.getTop(),
                            item.getRight(), item.getBottom());
                    bgDelete.draw(c);
                    paint.setTextSize(52f);
                    c.drawText("🗑", item.getRight() + dX + 16f,
                            item.getTop() + (item.getHeight() / 2f) + 18f, paint);
                }
                super.onChildDraw(c, rv, vh, dX, dY, action, active);
            }
        }).attachToRecyclerView(rv);
    }

    // ── ViewHolder factory ─────────────────────────────────────────────────────
    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_HEADER : TYPE_TASK;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(context);
        if (viewType == TYPE_HEADER) {
            View v = inf.inflate(R.layout.item_task_header, parent, false);
            return new HeaderViewHolder(v);
        }
        View v = inf.inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((String) items.get(position));
        } else {
            ((TaskViewHolder) holder).bind((Task) items.get(position));
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    // ── Header ViewHolder ──────────────────────────────────────────────────────
    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvHeader;
        HeaderViewHolder(@NonNull View v) {
            super(v);
            tvHeader = v.findViewById(R.id.tvSectionHeader);
        }
        void bind(String title) { tvHeader.setText(title); }
    }

    // ── Task ViewHolder ────────────────────────────────────────────────────────
    class TaskViewHolder extends RecyclerView.ViewHolder {
        View      priorityDot;
        CheckBox  cbTaskComplete;
        TextView  tvTaskTitle, tvTaskDate, tvTaskNote, tvDueDate, tvOverdue;
        ImageButton btnDelete;

        TaskViewHolder(@NonNull View v) {
            super(v);
            priorityDot    = v.findViewById(R.id.priorityDot);
            cbTaskComplete = v.findViewById(R.id.cbTaskComplete);
            tvTaskTitle    = v.findViewById(R.id.tvTaskTitle);
            tvTaskDate     = v.findViewById(R.id.tvTaskDate);
            tvTaskNote     = v.findViewById(R.id.tvTaskNote);
            tvDueDate      = v.findViewById(R.id.tvDueDate);
            tvOverdue      = v.findViewById(R.id.tvOverdue);
            btnDelete      = v.findViewById(R.id.btnDeleteTask);
        }

        void bind(Task task) {
            // ── Title + strikethrough ──────────────────────────────────────────
            tvTaskTitle.setText(task.getTitle());
            if (task.isCompleted()) {
                tvTaskTitle.setPaintFlags(
                        tvTaskTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                tvTaskTitle.setTextColor(0xFF506070);
            } else {
                tvTaskTitle.setPaintFlags(
                        tvTaskTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                tvTaskTitle.setTextColor(0xFFFFFFFF);
            }

            // ── Priority dot ──────────────────────────────────────────────────
            int dotColor;
            switch (task.getPriority()) {
                case Task.PRIORITY_HIGH:   dotColor = COLOR_HIGH;   break;
                case Task.PRIORITY_LOW:    dotColor = COLOR_LOW;    break;
                default:                   dotColor = COLOR_MEDIUM; break;
            }
            priorityDot.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(dotColor));

            // ── Note ──────────────────────────────────────────────────────────
            if (!task.getNote().isEmpty()) {
                tvTaskNote.setVisibility(View.VISIBLE);
                tvTaskNote.setText(task.getNote());
            } else {
                tvTaskNote.setVisibility(View.GONE);
            }

            // ── Created date ──────────────────────────────────────────────────
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            tvTaskDate.setText("Created " + sdf.format(new Date(task.getCreatedAt())));

            // ── Due date + overdue indicator ──────────────────────────────────
            if (task.hasDueDate()) {
                tvDueDate.setVisibility(View.VISIBLE);
                tvDueDate.setText("Due " + sdf.format(new Date(task.getDueDate())));
                tvDueDate.setTextColor(task.isOverdue() ? 0xFFEF5350 : 0xFF90CAF9);
            } else {
                tvDueDate.setVisibility(View.GONE);
            }

            if (task.isOverdue()) {
                tvOverdue.setVisibility(View.VISIBLE);
            } else {
                tvOverdue.setVisibility(View.GONE);
            }

            // ── Checkbox — remove old listener first to avoid false triggers ──
            cbTaskComplete.setOnCheckedChangeListener(null);
            cbTaskComplete.setChecked(task.isCompleted());
            cbTaskComplete.setOnCheckedChangeListener((btn, checked) -> {
                if (listener != null) listener.onTaskChecked(task, checked);
            });

            // ── Delete ────────────────────────────────────────────────────────
            btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onTaskDelete(task);
            });

            // ── Entrance animation ────────────────────────────────────────────
            itemView.setAlpha(0f);
            itemView.setTranslationX(30f);
            itemView.animate()
                    .alpha(1f).translationX(0f)
                    .setDuration(250)
                    .setStartDelay(getAdapterPosition() * 30L)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
    }
}