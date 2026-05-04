package com.example.studysync;

import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class FocusScoreDialog extends DialogFragment {

    private static final String ARG_SCORE       = "score";
    private static final String ARG_DISTRACTIONS = "distractions";

    public static FocusScoreDialog newInstance(int score, int distractions) {
        FocusScoreDialog d = new FocusScoreDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_SCORE, score);
        args.putInt(ARG_DISTRACTIONS, distractions);
        d.setArguments(args);
        return d;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        int score        = getArguments().getInt(ARG_SCORE);
        int distractions = getArguments().getInt(ARG_DISTRACTIONS);

        String emoji, grade, tip;
        if (score >= 90) {
            emoji = "🏆"; grade = "Excellent!";
            tip   = "You stayed fully focused. Keep it up!";
        } else if (score >= 70) {
            emoji = "👍"; grade = "Good Focus";
            tip   = "Minor distractions (" + distractions + "). Almost perfect!";
        } else if (score >= 50) {
            emoji = "😐"; grade = "Fair";
            tip   = distractions + " distractions pulled you away. Try again!";
        } else {
            emoji = "📵"; grade = "Keep Trying";
            tip   = "Put your phone down during sessions for better results.";
        }

        String message =
                emoji + " Score: " + score + " / 100\n\n" +
                        "Grade: " + grade + "\n" +
                        "Distractions: " + distractions + "\n\n" +
                        tip;

        return new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setTitle("Focus Report")
                .setMessage(message)
                .setPositiveButton("Done", null)
                .create();
    }
}