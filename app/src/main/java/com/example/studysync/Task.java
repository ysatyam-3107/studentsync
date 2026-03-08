package com.example.studysync;

public class Task {
    private String  id;
    private String  title;
    private String  note;        // optional extra detail
    private boolean completed;
    private long    createdAt;
    private long    dueDate;     // 0 = no due date
    private int     priority;    // 0=Low  1=Medium  2=High

    // Priority constants — use these everywhere for clarity
    public static final int PRIORITY_LOW    = 0;
    public static final int PRIORITY_MEDIUM = 1;
    public static final int PRIORITY_HIGH   = 2;

    public Task() { /* Required for Firebase */ }

    public Task(String id, String title, boolean completed, long createdAt) {
        this.id        = id;
        this.title     = title;
        this.completed = completed;
        this.createdAt = createdAt;
        this.priority  = PRIORITY_MEDIUM;
        this.dueDate   = 0;
        this.note      = "";
    }

    // ── Getters & Setters ──────────────────────────────────────────────────────
    public String  getId()                        { return id; }
    public void    setId(String id)               { this.id = id; }

    public String  getTitle()                     { return title; }
    public void    setTitle(String title)         { this.title = title; }

    public String  getNote()                      { return note != null ? note : ""; }
    public void    setNote(String note)           { this.note = note; }

    public boolean isCompleted()                  { return completed; }
    public void    setCompleted(boolean c)        { this.completed = c; }

    public long    getCreatedAt()                 { return createdAt; }
    public void    setCreatedAt(long t)           { this.createdAt = t; }

    public long    getDueDate()                   { return dueDate; }
    public void    setDueDate(long dueDate)       { this.dueDate = dueDate; }

    public int     getPriority()                  { return priority; }
    public void    setPriority(int priority)      { this.priority = priority; }

    // ── Helpers ────────────────────────────────────────────────────────────────
    public boolean isOverdue() {
        return !completed && dueDate > 0 && dueDate < System.currentTimeMillis();
    }

    public boolean hasDueDate() {
        return dueDate > 0;
    }
}