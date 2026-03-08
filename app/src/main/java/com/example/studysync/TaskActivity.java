package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TaskActivity extends AppCompatActivity {

    // ── Views ──────────────────────────────────────────────────────────────────
    private RecyclerView             rvTasks;
    private FloatingActionButton     fabAddTask;
    private TextView                 tvProgress;
    private ProgressBar              progressBar;
    private LinearLayout             tabAll, tabActive, tabDone;
    private View                     emptyState;
    private TextView                 tvEmptyMessage;

    // ── Adapter & data ─────────────────────────────────────────────────────────
    private TaskAdapter   taskAdapter;
    private List<Task>    allTasks    = new ArrayList<>();
    private String        activeFilter = "all"; // "all" | "active" | "done"

    // ── Firebase ───────────────────────────────────────────────────────────────
    private DatabaseReference  tasksRef;
    private FirebaseAuth       auth;
    private String             userId;
    private ValueEventListener taskListener;

    // ── Progress animation ─────────────────────────────────────────────────────
    private int currentProgress = 0;

    // ──────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task);

        initViews();
        initFirebase();
        setupRecyclerView();
        loadTasks();
        animateFab();

        BottomNavHelper.setup(this, BottomNavHelper.Tab.TASKS);
    }

    // ── View wiring ────────────────────────────────────────────────────────────
    private void initViews() {
        rvTasks       = findViewById(R.id.rvTasks);
        fabAddTask    = findViewById(R.id.fabAddTask);
        tvProgress    = findViewById(R.id.tvProgress);
        progressBar   = findViewById(R.id.progressBarTask);
        tabAll        = findViewById(R.id.tabAll);
        tabActive     = findViewById(R.id.tabActive);
        tabDone       = findViewById(R.id.tabDone);
        emptyState    = findViewById(R.id.emptyState);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);

        fabAddTask.setOnClickListener(v -> showAddTaskDialog());

        // Filter tabs
        tabAll.setOnClickListener(v    -> setFilter("all"));
        tabActive.setOnClickListener(v -> setFilter("active"));
        tabDone.setOnClickListener(v   -> setFilter("done"));
    }

    private void initFirebase() {
        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        userId   = auth.getCurrentUser().getUid();
        tasksRef = FirebaseDatabase.getInstance().getReference("Tasks").child(userId);
    }

    private void setupRecyclerView() {
        taskAdapter = new TaskAdapter(this, new TaskAdapter.OnTaskClickListener() {
            @Override
            public void onTaskChecked(Task task, boolean isChecked) {
                updateTaskCompletion(task.getId(), isChecked);
            }
            @Override
            public void onTaskDelete(Task task) {
                deleteTask(task.getId());
            }
        });

        rvTasks.setLayoutManager(new LinearLayoutManager(this));
        rvTasks.setAdapter(taskAdapter);

        // Attach swipe gestures
        taskAdapter.attachSwipeHelper(rvTasks);
    }

    // ── Load tasks from Firebase ───────────────────────────────────────────────
    private void loadTasks() {
        taskListener = tasksRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allTasks.clear();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    Task task = snap.getValue(Task.class);
                    if (task != null) {
                        task.setId(snap.getKey());
                        allTasks.add(task);
                    }
                }
                applyFilter();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TaskActivity.this,
                        "Failed to load tasks", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Filter logic ───────────────────────────────────────────────────────────
    private void setFilter(String filter) {
        activeFilter = filter;

        // Update tab styles
        int active   = 0xFFFFFFFF;
        int inactive = 0xFF506070;
        setTabStyle(tabAll,    "all".equals(filter),    active, inactive);
        setTabStyle(tabActive, "active".equals(filter), active, inactive);
        setTabStyle(tabDone,   "done".equals(filter),   active, inactive);

        applyFilter();
    }

    private void setTabStyle(LinearLayout tab, boolean isActive, int activeColor, int inactiveColor) {
        tab.setBackgroundResource(isActive ? R.drawable.tab_active_bg : R.drawable.tab_inactive_bg);
        for (int i = 0; i < tab.getChildCount(); i++) {
            View child = tab.getChildAt(i);
            if (child instanceof TextView)
                ((TextView) child).setTextColor(isActive ? activeColor : inactiveColor);
        }
    }

    private void applyFilter() {
        List<Task> pending = new ArrayList<>();
        List<Task> done    = new ArrayList<>();

        for (Task t : allTasks) {
            boolean matchesFilter = "all".equals(activeFilter)
                    || ("active".equals(activeFilter) && !t.isCompleted())
                    || ("done".equals(activeFilter)   &&  t.isCompleted());

            if (matchesFilter) {
                if (t.isCompleted()) done.add(t);
                else                 pending.add(t);
            }
        }

        taskAdapter.submitList(pending, done);

        // Empty state
        boolean isEmpty = pending.isEmpty() && done.isEmpty();
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvTasks.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        if (isEmpty) {
            switch (activeFilter) {
                case "active": tvEmptyMessage.setText("No pending tasks 🎉\nYou're all caught up!"); break;
                case "done":   tvEmptyMessage.setText("No completed tasks yet.\nGet started! 💪");   break;
                default:       tvEmptyMessage.setText("No tasks yet.\nTap + to add your first one!"); break;
            }
        }

        // Update progress bar
        int total     = allTasks.size();
        int completed = 0;
        for (Task t : allTasks) if (t.isCompleted()) completed++;
        updateProgress(completed, total);
    }

    // ── Add Task Dialog ────────────────────────────────────────────────────────
    private void showAddTaskDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_task, null);

        TextInputEditText etTitle    = dialogView.findViewById(R.id.etTaskTitle);
        TextInputEditText etNote     = dialogView.findViewById(R.id.etTaskNote);
        TextView          tvDueDate  = dialogView.findViewById(R.id.tvSelectedDueDate);
        View              btnPickDate = dialogView.findViewById(R.id.btnPickDate);
        LinearLayout      priorityLow  = dialogView.findViewById(R.id.priorityLow);
        LinearLayout      priorityMed  = dialogView.findViewById(R.id.priorityMedium);
        LinearLayout      priorityHigh = dialogView.findViewById(R.id.priorityHigh);

        final int[]  selectedPriority = {Task.PRIORITY_MEDIUM};
        final long[] selectedDueDate  = {0};

        // Priority selector
        setPrioritySelected(priorityLow, priorityMed, priorityHigh, selectedPriority[0]);
        priorityLow.setOnClickListener(v -> {
            selectedPriority[0] = Task.PRIORITY_LOW;
            setPrioritySelected(priorityLow, priorityMed, priorityHigh, Task.PRIORITY_LOW);
        });
        priorityMed.setOnClickListener(v -> {
            selectedPriority[0] = Task.PRIORITY_MEDIUM;
            setPrioritySelected(priorityLow, priorityMed, priorityHigh, Task.PRIORITY_MEDIUM);
        });
        priorityHigh.setOnClickListener(v -> {
            selectedPriority[0] = Task.PRIORITY_HIGH;
            setPrioritySelected(priorityLow, priorityMed, priorityHigh, Task.PRIORITY_HIGH);
        });

        // Date picker
        btnPickDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this, (picker, year, month, day) -> {
                Calendar picked = Calendar.getInstance();
                picked.set(year, month, day, 23, 59, 0);
                selectedDueDate[0] = picked.getTimeInMillis();
                tvDueDate.setText(new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(new Date(selectedDueDate[0])));
                tvDueDate.setTextColor(0xFF4CAF50);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        // Build dark-themed dialog
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.DarkDialogTheme)
                .setView(dialogView)
                .setTitle("New Task")
                .setPositiveButton("Add Task", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = etTitle.getText() != null
                    ? etTitle.getText().toString().trim() : "";
            if (title.isEmpty()) {
                etTitle.setError("Title required");
                return;
            }
            String note = etNote.getText() != null
                    ? etNote.getText().toString().trim() : "";
            addTask(title, note, selectedPriority[0], selectedDueDate[0]);
            dialog.dismiss();
        });
    }

    // Highlight the selected priority chip
    private void setPrioritySelected(View low, View med, View high, int selected) {
        low.setAlpha( selected == Task.PRIORITY_LOW    ? 1f : 0.4f);
        med.setAlpha( selected == Task.PRIORITY_MEDIUM ? 1f : 0.4f);
        high.setAlpha(selected == Task.PRIORITY_HIGH   ? 1f : 0.4f);
    }

    // ── Firebase CRUD ──────────────────────────────────────────────────────────
    private void addTask(String title, String note, int priority, long dueDate) {
        String taskId = tasksRef.push().getKey();
        if (taskId == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("title",     title);
        data.put("note",      note);
        data.put("priority",  priority);
        data.put("dueDate",   dueDate);
        data.put("completed", false);
        data.put("createdAt", ServerValue.TIMESTAMP);

        tasksRef.child(taskId).setValue(data)
                .addOnSuccessListener(v -> Toast.makeText(this, "Task added ✅", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to add task", Toast.LENGTH_SHORT).show());
    }

    private void updateTaskCompletion(String taskId, boolean completed) {
        tasksRef.child(taskId).child("completed").setValue(completed);
    }

    private void deleteTask(String taskId) {
        new AlertDialog.Builder(this, R.style.DarkDialogTheme)
                .setTitle("Delete Task")
                .setMessage("Are you sure you want to delete this task?")
                .setPositiveButton("Delete", (dialog, which) ->
                        tasksRef.child(taskId).removeValue()
                                .addOnSuccessListener(v ->
                                        Toast.makeText(this, "Task deleted", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Progress bar — animates from previous value ────────────────────────────
    private void updateProgress(int completed, int total) {
        if (total == 0) {
            tvProgress.setText("No tasks yet — add your first one!");
            animateProgress(0);
            return;
        }
        int pct = (completed * 100) / total;
        String emoji = pct == 100 ? " 🎉" : pct >= 50 ? " 💪" : "";
        tvProgress.setText(completed + "/" + total + " completed (" + pct + "%)" + emoji);
        animateProgress(pct);
    }

    private void animateProgress(int targetPct) {
        ObjectAnimator anim = ObjectAnimator.ofInt(progressBar, "progress",
                currentProgress, targetPct);
        anim.setDuration(600);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.start();
        currentProgress = targetPct;
    }

    // ── FAB bounce entrance animation ─────────────────────────────────────────
    private void animateFab() {
        fabAddTask.setScaleX(0f);
        fabAddTask.setScaleY(0f);
        fabAddTask.setAlpha(0f);
        fabAddTask.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(500)
                .setStartDelay(300)
                .setInterpolator(new OvershootInterpolator(2.5f))
                .start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (taskListener != null && tasksRef != null)
            tasksRef.removeEventListener(taskListener);
    }
}