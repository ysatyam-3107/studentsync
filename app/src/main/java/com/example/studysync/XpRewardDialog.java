package com.example.studysync;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows XP earned + streak + newly unlocked badges after a session.
 * Displayed right after FocusScoreDialog.
 */
public class XpRewardDialog extends DialogFragment {

    private static final String ARG_XP_EARNED   = "xp_earned";
    private static final String ARG_XP_TOTAL    = "xp_total";
    private static final String ARG_STREAK      = "streak";
    private static final String ARG_BADGES      = "badges";

    public static XpRewardDialog newInstance(int xpEarned, int xpTotal,
                                             int streak, List<String> newBadges) {
        XpRewardDialog d = new XpRewardDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_XP_EARNED, xpEarned);
        args.putInt(ARG_XP_TOTAL,  xpTotal);
        args.putInt(ARG_STREAK,    streak);
        ArrayList<String> list = new ArrayList<>(newBadges);
        args.putStringArrayList(ARG_BADGES, list);
        d.setArguments(args);
        return d;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        int         xpEarned  = getArguments().getInt(ARG_XP_EARNED);
        int         xpTotal   = getArguments().getInt(ARG_XP_TOTAL);
        int         streak    = getArguments().getInt(ARG_STREAK);
        List<String> badges   = getArguments().getStringArrayList(ARG_BADGES);

        // ── Build message ─────────────────────────────────────────────────
        StringBuilder msg = new StringBuilder();

        // XP line
        msg.append("⚡ +").append(xpEarned).append(" XP earned!\n");
        msg.append("Total XP: ").append(xpTotal).append("\n\n");

        // Streak line
        if (streak >= 7) {
            msg.append("⚡ ").append(streak).append("-day streak! On fire!\n\n");
        } else if (streak >= 3) {
            msg.append("🔥 ").append(streak).append("-day streak! Keep going!\n\n");
        } else if (streak == 1) {
            msg.append("📅 Day 1 streak started!\n\n");
        } else {
            msg.append("📅 ").append(streak).append("-day streak\n\n");
        }

        // XP breakdown
        msg.append("Breakdown:\n");
        msg.append("  • Session completed: +").append(XpManager.XP_SESSION_COMPLETE).append(" XP\n");
        if (xpEarned > XpManager.XP_SESSION_COMPLETE) {
            if (xpEarned - XpManager.XP_SESSION_COMPLETE >= XpManager.XP_PERFECT_FOCUS) {
                msg.append("  • Perfect focus bonus: +").append(XpManager.XP_PERFECT_FOCUS).append(" XP\n");
            }
            int streakBonus = xpEarned - XpManager.XP_SESSION_COMPLETE -
                    (xpEarned - XpManager.XP_SESSION_COMPLETE >= XpManager.XP_PERFECT_FOCUS
                            ? XpManager.XP_PERFECT_FOCUS : 0);
            if (streakBonus > 0) {
                msg.append("  • Streak bonus: +").append(streakBonus).append(" XP\n");
            }
        }

        // New badges
        if (badges != null && !badges.isEmpty()) {
            msg.append("\n🏅 New badge").append(badges.size() > 1 ? "s" : "").append(" unlocked!\n");
            for (String badge : badges) {
                msg.append("  ")
                        .append(XpManager.getBadgeEmoji(badge))
                        .append(" ")
                        .append(XpManager.getBadgeLabel(badge))
                        .append("\n");
            }
        }

        return new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setTitle("Session Complete!")
                .setMessage(msg.toString().trim())
                .setPositiveButton("Awesome!", null)
                .create();
    }
}