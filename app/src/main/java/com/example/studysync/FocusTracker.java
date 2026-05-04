package com.example.studysync;

public class FocusTracker {

    private int  distractionCount  = 0;
    private long pauseTimestamp    = 0;
    private long totalDistractedMs = 0;
    private boolean sessionActive  = false;

    /** Call when a work Pomodoro starts. */
    public void startSession() {
        distractionCount  = 0;
        totalDistractedMs = 0;
        pauseTimestamp    = 0;
        sessionActive     = true;
    }

    /** Call from onStop() — user left the app. */
    public void onUserLeft() {
        if (!sessionActive) return;
        distractionCount++;
        pauseTimestamp = System.currentTimeMillis();
    }

    /** Call from onStart() — user returned to the app. */
    public void onUserReturned() {
        if (!sessionActive || pauseTimestamp == 0) return;
        totalDistractedMs += System.currentTimeMillis() - pauseTimestamp;
        pauseTimestamp = 0;
    }

    /**
     * Calculate focus score 0-100.
     * Penalty 1 : each distraction costs 10 pts  (max -50)
     * Penalty 2 : time away as % of session       (max -50)
     */
    public int calculateScore(long sessionDurationMs) {
        sessionActive = false;

        int distractionPenalty = Math.min(distractionCount * 10, 50);

        int timePenalty = 0;
        if (sessionDurationMs > 0) {
            double awayRatio = (double) totalDistractedMs / sessionDurationMs;
            timePenalty = (int) Math.min(awayRatio * 100, 50);
        }

        return Math.max(0, 100 - distractionPenalty - timePenalty);
    }

    public void stopSession()          { sessionActive = false; }
    public boolean isSessionActive()   { return sessionActive; }
    public int  getDistractionCount()  { return distractionCount; }
    public long getTotalDistractedMs() { return totalDistractedMs; }
}