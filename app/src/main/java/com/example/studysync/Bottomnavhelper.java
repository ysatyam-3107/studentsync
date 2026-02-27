package com.example.studysync;

import android.content.Intent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * BottomNavHelper
 *
 * Drop this one call into every Activity that shows the nav bar:
 *
 *   BottomNavHelper.setup(this, BottomNavHelper.Tab.HOME);
 *
 * The layout_bottom_nav.xml must be <include>d in the activity's layout.
 */
public class Bottomnavhelper {

    public enum Tab { HOME, ROOMS, TASKS, PROFILE }

    private static final int COLOR_ACTIVE   = 0xFF4CAF50; // green
    private static final int COLOR_INACTIVE = 0xFF546E7A; // blue-grey

    public static void setup(AppCompatActivity activity, Tab activeTab) {

        View navHome    = activity.findViewById(R.id.navHome);
        View navRooms   = activity.findViewById(R.id.navRooms);
        View navTasks   = activity.findViewById(R.id.navTasks);
        View navProfile = activity.findViewById(R.id.navProfile);

        if (navHome == null) return; // nav bar not in this layout

        // Style each tab
        styleTab(activity, navHome,    R.id.navHomeIcon,    R.id.navHomeLabel,    R.id.navHomeDot,    activeTab == Tab.HOME);
        styleTab(activity, navRooms,   R.id.navRoomsIcon,   R.id.navRoomsLabel,   R.id.navRoomsDot,   activeTab == Tab.ROOMS);
        styleTab(activity, navTasks,   R.id.navTasksIcon,   R.id.navTasksLabel,   R.id.navTasksDot,   activeTab == Tab.TASKS);
        styleTab(activity, navProfile, R.id.navProfileIcon, R.id.navProfileLabel, R.id.navProfileDot, activeTab == Tab.PROFILE);

        // Animate navbar in from bottom
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

        // Click listeners — navigate to the right activity
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

    private static void styleTab(AppCompatActivity activity,
                                 View container,
                                 int iconId, int labelId, int dotId,
                                 boolean active) {
        ImageView icon  = container.findViewById(iconId);
        TextView  label = container.findViewById(labelId);
        View      dot   = container.findViewById(dotId);

        int color = active ? COLOR_ACTIVE : COLOR_INACTIVE;
        if (icon  != null) icon.setColorFilter(color);
        if (label != null) {
            label.setTextColor(color);
            label.setTextSize(active ? 10.5f : 10f);
        }
        if (dot != null) dot.setVisibility(active ? View.VISIBLE : View.INVISIBLE);

        // Scale up the active icon slightly
        if (icon != null) {
            float scale = active ? 1.15f : 1f;
            icon.setScaleX(scale);
            icon.setScaleY(scale);
        }
    }

    /** Bounce animation when tab is tapped */
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

    private static void navigateTo(AppCompatActivity from, Class<?> to) {
        Intent intent = new Intent(from, to);
        // Smooth slide transition
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        from.startActivity(intent);
        from.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }
}