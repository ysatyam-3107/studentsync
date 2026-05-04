package com.example.studysync;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * XpManager — handles XP points, streaks, and badge unlocks.
 *
 * Firebase structure written by this class:
 *
 * users/{uid}/xp              → int   (total XP)
 * users/{uid}/streak          → int   (current daily streak)
 * users/{uid}/lastStudyDay    → long  (epoch ms of last session, midnight-normalised)
 * users/{uid}/totalSessions   → int   (all-time completed sessions)
 * users/{uid}/badges/{name}   → true  (unlocked badges)
 */
public class XpManager {

    // ── XP rewards ────────────────────────────────────────────────────────────
    public static final int XP_SESSION_COMPLETE  = 50;
    public static final int XP_PERFECT_FOCUS     = 30;  // score >= 90
    public static final int XP_STREAK_BONUS      = 20;  // per day in streak

    // ── Badge keys ────────────────────────────────────────────────────────────
    public static final String BADGE_FIRST_SESSION  = "first_session";
    public static final String BADGE_STREAK_3       = "streak_3";
    public static final String BADGE_STREAK_7       = "streak_7";
    public static final String BADGE_SESSIONS_10    = "sessions_10";
    public static final String BADGE_SESSIONS_25    = "sessions_25";
    public static final String BADGE_PERFECT_FOCUS  = "perfect_focus";
    public static final String BADGE_IRON_WILL      = "iron_will";   // score 100

    public interface OnRewardListener {
        void onReward(int xpEarned, int newTotal, int streak, List<String> newBadges);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Call this after every completed work Pomodoro session.
     * Reads current state, calculates rewards, writes back atomically.
     */
    public static void onSessionCompleted(int focusScore, OnRewardListener listener) {
        String uid = getCurrentUid();
        if (uid == null) return;

        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users").child(uid);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                // ── Read current state ────────────────────────────────────
                int  currentXp       = getInt(snapshot, "xp",            0);
                int  currentStreak   = getInt(snapshot, "streak",        0);
                int  totalSessions   = getInt(snapshot, "totalSessions", 0);
                long lastStudyDay    = getLong(snapshot, "lastStudyDay", 0L);

                // ── Streak logic ──────────────────────────────────────────
                long todayMidnight   = getMidnightMs();
                long yesterdayMidnight = todayMidnight - 86_400_000L;

                int newStreak;
                if (lastStudyDay == todayMidnight) {
                    // Already studied today — keep streak, no bonus again
                    newStreak = currentStreak;
                } else if (lastStudyDay == yesterdayMidnight) {
                    // Studied yesterday — extend streak
                    newStreak = currentStreak + 1;
                } else {
                    // Missed a day — reset streak
                    newStreak = 1;
                }

                // ── XP calculation ────────────────────────────────────────
                int xpEarned = XP_SESSION_COMPLETE;
                if (focusScore >= 90) xpEarned += XP_PERFECT_FOCUS;
                if (newStreak > 1)    xpEarned += XP_STREAK_BONUS * (newStreak - 1);

                int newXp           = currentXp + xpEarned;
                int newTotalSessions = totalSessions + 1;

                // ── Badge checks ──────────────────────────────────────────
                List<String> newBadges = new ArrayList<>();
                DataSnapshot badgesSnap = snapshot.child("badges");

                checkBadge(badgesSnap, BADGE_FIRST_SESSION,
                        newTotalSessions >= 1,  newBadges);
                checkBadge(badgesSnap, BADGE_SESSIONS_10,
                        newTotalSessions >= 10, newBadges);
                checkBadge(badgesSnap, BADGE_SESSIONS_25,
                        newTotalSessions >= 25, newBadges);
                checkBadge(badgesSnap, BADGE_STREAK_3,
                        newStreak >= 3,  newBadges);
                checkBadge(badgesSnap, BADGE_STREAK_7,
                        newStreak >= 7,  newBadges);
                checkBadge(badgesSnap, BADGE_PERFECT_FOCUS,
                        focusScore >= 90, newBadges);
                checkBadge(badgesSnap, BADGE_IRON_WILL,
                        focusScore >= 100, newBadges);

                // ── Write back ────────────────────────────────────────────
                Map<String, Object> updates = new HashMap<>();
                updates.put("xp",            newXp);
                updates.put("streak",        newStreak);
                updates.put("lastStudyDay",  todayMidnight);
                updates.put("totalSessions", newTotalSessions);
                for (String badge : newBadges) {
                    updates.put("badges/" + badge, true);
                }

                userRef.updateChildren(updates);

                // ── Notify caller ─────────────────────────────────────────
                if (listener != null) {
                    listener.onReward(xpEarned, newXp, newStreak, newBadges);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void checkBadge(DataSnapshot badges, String key,
                                   boolean condition, List<String> newBadges) {
        if (condition && !badges.hasChild(key)) {
            newBadges.add(key);
        }
    }

    /** Returns today's date at 00:00:00 local time as epoch ms */
    static long getMidnightMs() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE,      0);
        c.set(Calendar.SECOND,      0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static int getInt(DataSnapshot s, String key, int def) {
        Integer v = s.child(key).getValue(Integer.class);
        return v != null ? v : def;
    }

    private static long getLong(DataSnapshot s, String key, long def) {
        Long v = s.child(key).getValue(Long.class);
        return v != null ? v : def;
    }

    private static String getCurrentUid() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return null;
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    /** Human-readable label for a badge key */
    public static String getBadgeLabel(String key) {
        switch (key) {
            case BADGE_FIRST_SESSION:  return "First Session";
            case BADGE_STREAK_3:       return "3-Day Streak";
            case BADGE_STREAK_7:       return "7-Day Streak";
            case BADGE_SESSIONS_10:    return "10 Sessions";
            case BADGE_SESSIONS_25:    return "25 Sessions";
            case BADGE_PERFECT_FOCUS:  return "Perfect Focus";
            case BADGE_IRON_WILL:      return "Iron Will";
            default:                   return key;
        }
    }

    /** Emoji icon for a badge key */
    public static String getBadgeEmoji(String key) {
        switch (key) {
            case BADGE_FIRST_SESSION:  return "🎯";
            case BADGE_STREAK_3:       return "🔥";
            case BADGE_STREAK_7:       return "⚡";
            case BADGE_SESSIONS_10:    return "📚";
            case BADGE_SESSIONS_25:    return "🏆";
            case BADGE_PERFECT_FOCUS:  return "💎";
            case BADGE_IRON_WILL:      return "🦾";
            default:                   return "⭐";
        }
    }
}