package com.example.studysync;

import android.content.Intent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * BottomNavHelper
 *
 * Add one line to every Activity that has the nav bar:
 *   BottomNavHelper.setup(this, BottomNavHelper.Tab.HOME);
 *
 * Requires layout_bottom_nav.xml to be <include>d in the activity layout.
 * Anim files slide_in_right.xml and slide_out_left.xml must be in res/anim/
 *
 * Also add to AndroidManifest.xml for each nav activity:
 *   android:launchMode="singleTop"
 */
public class BottomNavHelper {

    public enum Tab { HOME, ROOMS, TASKS, PROFILE }

    private static final int COLOR_ACTIVE   = 0xFF4CAF50; // green
    private static final int COLOR_INACTIVE = 0xFF546E7A; // blue-grey

    public static void setup(AppCompatActivity activity, Tab activeTab) {

        View navHome    = activity.findViewById(R.id.navHome);
        View navRooms   = activity.findViewById(R.id.navRooms);
        View navTasks   = activity.findViewById(R.id.navTasks);
        View navProfile = activity.findViewById(R.id.navProfile);

        // Safety: if nav bar is not included in this layout, do nothing
        if (navHome == null) return;

        // Apply active/inactive styling to each tab
        styleTab(navHome,    R.id.navHomeIcon,    R.id.navHomeLabel,    R.id.navHomeDot,    activeTab == Tab.HOME);
        styleTab(navRooms,   R.id.navRoomsIcon,   R.id.navRoomsLabel,   R.id.navRoomsDot,   activeTab == Tab.ROOMS);
        styleTab(navTasks,   R.id.navTasksIcon,   R.id.navTasksLabel,   R.id.navTasksDot,   activeTab == Tab.TASKS);
        styleTab(navProfile, R.id.navProfileIcon, R.id.navProfileLabel, R.id.navProfileDot, activeTab == Tab.PROFILE);

        // Animate the whole navbar sliding up on enter
        View navCard = activity.findViewById(R.id.bottomNavCard);
        if (navCard != null) {
            navCard.setTranslationY(200f);
            navCard.setAlpha(0f);
            navCard.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(400)
                    .setInterpolator(new OvershootInterpolator(0.6f))
                    .setStartDelay(150)
                    .start();
        }

        // ── Navigation click listeners ──────────────────────────────────
        navHome.setOnClickListener(v -> {
            if (activeTab != Tab.HOME) {
                animateTabClick(navHome);
                navigateTo(activity, MainActivity.class);
            }
        });

        navRooms.setOnClickListener(v -> {
            if (activeTab != Tab.ROOMS) {
                animateTabClick(navRooms);
                navigateTo(activity, MyRoomsActivity.class);
            }
        });

        navTasks.setOnClickListener(v -> {
            if (activeTab != Tab.TASKS) {
                animateTabClick(navTasks);
                navigateTo(activity, TaskActivity.class);
            }
        });

        navProfile.setOnClickListener(v -> {
            if (activeTab != Tab.PROFILE) {
                animateTabClick(navProfile);
                navigateTo(activity, ProfileActivity.class);
            }
        });
    }

    private static void styleTab(View container,
                                 int iconId, int labelId, int dotId,
                                 boolean active) {
        ImageView icon  = container.findViewById(iconId);
        TextView  label = container.findViewById(labelId);
        View      dot   = container.findViewById(dotId);

        int color = active ? COLOR_ACTIVE : COLOR_INACTIVE;

        if (icon != null) {
            icon.setColorFilter(color);
            float scale = active ? 1.15f : 1f;
            icon.setScaleX(scale);
            icon.setScaleY(scale);
        }
        if (label != null) {
            label.setTextColor(color);
            label.setTextSize(active ? 10.5f : 10f);
        }
        if (dot != null) {
            dot.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
        }
    }

    /** Bounce animation when a tab is tapped */
    private static void animateTabClick(View tab) {
        tab.animate()
                .scaleX(0.88f).scaleY(0.88f)
                .setDuration(80)
                .withEndAction(() ->
                        tab.animate()
                                .scaleX(1f).scaleY(1f)
                                .setDuration(150)
                                .setInterpolator(new OvershootInterpolator(2f))
                                .start())
                .start();
    }

    /**
     * Navigate to a tab activity.
     *
     * Uses FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP so that:
     * - If the activity is already in the back stack it gets brought to front
     *   WITHOUT creating a new instance (fixes the "stuck" bug)
     * - The back stack doesn't keep growing with every nav tap
     */
    private static void navigateTo(AppCompatActivity from, Class<?> to) {
        // Don't navigate if we're already on this screen
        if (from.getClass().equals(to)) return;

        Intent intent = new Intent(from, to);
        // KEY FIX: CLEAR_TOP brings existing instance to front and clears above it.
        // SINGLE_TOP prevents creating a duplicate if already on top.
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        from.startActivity(intent);
        from.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }
}