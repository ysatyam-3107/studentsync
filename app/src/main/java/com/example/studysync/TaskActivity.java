package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskActivity extends AppCompatActivity {

    private RecyclerView rvTasks;
    private Button btnAddTask;
    private TextView tvProgress;
    private ProgressBar progressBar;

    private TaskAdapter taskAdapter;
    private List<Task> taskList;

    private DatabaseReference tasksRef;
    private FirebaseAuth auth;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task);

        // Initialize views
        rvTasks = findViewById(R.id.rvTasks);
        btnAddTask = findViewById(R.id.btnAddTask);
        tvProgress = findViewById(R.id.tvProgress);
        progressBar = findViewById(R.id.progressBarTask);

        // Firebase
        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userId = auth.getCurrentUser().getUid();
        tasksRef = FirebaseDatabase.getInstance().getReference("Tasks").child(userId);

        // Setup RecyclerView
        taskList = new ArrayList<>();
        taskAdapter = new TaskAdapter(this, taskList, new TaskAdapter.OnTaskClickListener() {
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

        // Load tasks
        loadTasks();

        // Add task button
        btnAddTask.setOnClickListener(v -> showAddTaskDialog());
    }

    private void loadTasks() {
        tasksRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                taskList.clear();
                int completed = 0;
                int total = 0;

                for (DataSnapshot taskSnap : snapshot.getChildren()) {
                    Task task = taskSnap.getValue(Task.class);
                    if (task != null) {
                        task.setId(taskSnap.getKey());
                        taskList.add(task);
                        total++;
                        if (task.isCompleted()) completed++;
                    }
                }

                taskAdapter.notifyDataSetChanged();
                updateProgress(completed, total);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TaskActivity.this,
                        "Failed to load tasks", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddTaskDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_task, null);

        EditText etTaskTitle = dialogView.findViewById(R.id.etTaskTitle);
        Button btnSave = dialogView.findViewById(R.id.btnSaveTask);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelTask);

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        btnSave.setOnClickListener(v -> {
            String title = etTaskTitle.getText().toString().trim();
            if (title.isEmpty()) {
                etTaskTitle.setError("Task title required");
                return;
            }
            addTask(title);
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void addTask(String title) {
        String taskId = tasksRef.push().getKey();
        if (taskId == null) return;

        Map<String, Object> taskData = new HashMap<>();
        taskData.put("title", title);
        taskData.put("completed", false);
        taskData.put("createdAt", ServerValue.TIMESTAMP);

        tasksRef.child(taskId).setValue(taskData)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this, "Task added", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to add task", Toast.LENGTH_SHORT).show());
    }

    private void updateTaskCompletion(String taskId, boolean completed) {
        tasksRef.child(taskId).child("completed").setValue(completed);
    }

    private void deleteTask(String taskId) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Task")
                .setMessage("Are you sure you want to delete this task?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    tasksRef.child(taskId).removeValue()
                            .addOnSuccessListener(aVoid ->
                                    Toast.makeText(this, "Task deleted", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateProgress(int completed, int total) {
        if (total == 0) {
            tvProgress.setText("No tasks yet");
            progressBar.setProgress(0);
        } else {
            int percentage = (completed * 100) / total;
            tvProgress.setText(completed + "/" + total + " tasks completed (" + percentage + "%)");
            progressBar.setProgress(percentage);
        }
    }
}