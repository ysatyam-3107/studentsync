package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // ── Views ──────────────────────────────────────────────────────────────────
    private TextView    tvWelcome, tvRoomCount, tvQuote, tvTip;
    private TextView    tvStreak, tvSessionCount;
    private TextView    tvStatRooms, tvStatNotes, tvStatMessages;
    private TextView    tvNoRecentRooms, tvSeeAllRooms;
    private ImageView   ivProfile;
    private CardView    cardActiveRooms, cardCreateRoom, cardJoinRoom, cardTip;
    private LinearLayout recentRoomsContainer;

    // ── Firebase ───────────────────────────────────────────────────────────────
    private FirebaseAuth      auth;
    private DatabaseReference roomsRef;
    private DatabaseReference usersRef;

    // ── State ──────────────────────────────────────────────────────────────────
    private String currentUid;

    // ── Motivational quotes — rotates daily ───────────────────────────────────
    private static final String[] QUOTES = {
            "The secret of getting ahead is getting started.",
            "An investment in knowledge pays the best interest.",
            "Study hard, for the well is deep.",
            "You don't have to be great to start, but you have to start to be great.",
            "Education is the most powerful weapon you can use to change the world.",
            "The more that you read, the more things you will know.",
            "Success is the sum of small efforts repeated day in and day out.",
            "Believe you can and you're halfway there.",
            "The expert in anything was once a beginner.",
            "Push yourself, because no one else is going to do it for you.",
            "Great things never come from comfort zones.",
            "Dream it. Wish it. Do it.",
            "Work hard in silence. Let your success be your noise.",
            "Focus on being productive instead of busy.",
            "Don't watch the clock; do what it does. Keep going."
    };

    // ── Study tips — rotates daily ─────────────────────────────────────────────
    private static final String[] TIPS = {
            "Use the Pomodoro technique — 25 min focus, 5 min break.",
            "Teach what you've learned to someone else to retain it better.",
            "Review your notes within 24 hours to boost long-term memory.",
            "Break big topics into small chunks and tackle one at a time.",
            "Avoid multitasking — deep focus beats scattered attention.",
            "Use active recall instead of re-reading: quiz yourself.",
            "Sleep is when your brain consolidates memories — don't skip it.",
            "Study in the same spot each day to build a focus habit.",
            "Switch subjects after 90 minutes to stay sharp.",
            "Handwriting notes beats typing for understanding complex ideas.",
            "Use spaced repetition to review old material at increasing intervals.",
            "Start with the hardest task first when your energy is highest.",
            "Eliminate phone notifications during study sessions.",
            "A 10-minute walk before studying improves concentration.",
            "Set a specific goal for each session — not just 'study maths'."
    };

    // ──────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        currentUid = user.getUid();
        usersRef   = FirebaseDatabase.getInstance().getReference("Users");
        roomsRef   = FirebaseDatabase.getInstance().getReference("Rooms");

        initViews();
        setDailyQuoteAndTip();
        setGreeting(currentUid);
        loadUserPhoto(currentUid);
        loadRoomCount(currentUid);
        loadStats(currentUid);
        loadRecentRooms(currentUid);
        loadStreakAndSessions(currentUid);
        animateEntrance();

        BottomNavHelper.setup(this, BottomNavHelper.Tab.HOME);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentUid != null) {
            loadUserPhoto(currentUid);
            loadRecentRooms(currentUid);   // refresh chips when coming back
            loadStats(currentUid);
        }
    }

    // ── View wiring ────────────────────────────────────────────────────────────
    private void initViews() {
        tvWelcome          = findViewById(R.id.tvWelcome);
        tvRoomCount        = findViewById(R.id.tvRoomCount);
        tvQuote            = findViewById(R.id.tvQuote);
        tvTip              = findViewById(R.id.tvTip);
        tvStreak           = findViewById(R.id.tvStreak);
        tvSessionCount     = findViewById(R.id.tvSessionCount);
        tvStatRooms        = findViewById(R.id.tvStatRooms);
        tvStatNotes        = findViewById(R.id.tvStatNotes);
        tvStatMessages     = findViewById(R.id.tvStatMessages);
        tvNoRecentRooms    = findViewById(R.id.tvNoRecentRooms);
        tvSeeAllRooms      = findViewById(R.id.tvSeeAllRooms);
        ivProfile          = findViewById(R.id.ivProfile);
        cardActiveRooms    = findViewById(R.id.cardActiveRooms);
        cardCreateRoom     = findViewById(R.id.cardCreateRoom);
        cardJoinRoom       = findViewById(R.id.cardJoinRoom);
        recentRoomsContainer = findViewById(R.id.recentRoomsContainer);

        // Profile photo → ProfileActivity
        ivProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        // Active Rooms card → MyRoomsActivity
        cardActiveRooms.setOnClickListener(v ->
                startActivity(new Intent(this, MyRoomsActivity.class)));

        // "See all" → MyRoomsActivity
        tvSeeAllRooms.setOnClickListener(v ->
                startActivity(new Intent(this, MyRoomsActivity.class)));

        // Create Room → StudyRoomActivity with mode flag
        cardCreateRoom.setOnClickListener(v -> {
            animateCard(cardCreateRoom);
            Intent i = new Intent(this, StudyRoomActivity.class);
            i.putExtra("mode", "create");
            startActivity(i);
        });

        // Join Room → StudyRoomActivity with mode flag
        cardJoinRoom.setOnClickListener(v -> {
            animateCard(cardJoinRoom);
            Intent i = new Intent(this, StudyRoomActivity.class);
            i.putExtra("mode", "join");
            startActivity(i);
        });
    }

    // ── Daily quote + tip (deterministic by day-of-year) ──────────────────────
    private void setDailyQuoteAndTip() {
        int dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        tvQuote.setText(QUOTES[dayOfYear % QUOTES.length]);
        tvTip.setText(TIPS[dayOfYear % TIPS.length]);
    }

    // ── Time-of-day greeting with user's name ──────────────────────────────────
    private void setGreeting(String uid) {
        usersRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String name = snapshot.child("name").getValue(String.class);
                if (name == null || name.isEmpty()) name = "there";

                // First name only
                String firstName = name.split(" ")[0];

                int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                String greeting = hour < 12 ? "Good Morning ☀️"
                        : hour < 17 ? "Good Afternoon 👋"
                        : "Good Evening 🌙";

                tvWelcome.setText(greeting + ", " + firstName + "!");
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // ── Profile photo ──────────────────────────────────────────────────────────
    private void loadUserPhoto(String uid) {
        usersRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String photoUrl = snapshot.child("photoUrl").getValue(String.class);
                if (photoUrl != null && !photoUrl.isEmpty()) {
                    Glide.with(MainActivity.this)
                            .load(photoUrl)
                            .transform(new CircleCrop())
                            .placeholder(R.drawable.ic_profile_circle)
                            .error(R.drawable.ic_profile_circle)
                            .into(ivProfile);
                } else {
                    ivProfile.setImageResource(R.drawable.ic_profile_circle);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                ivProfile.setImageResource(R.drawable.ic_profile_circle);
            }
        });
    }

    // ── Active room count ──────────────────────────────────────────────────────
    private void loadRoomCount(String uid) {
        roomsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int count = 0;
                for (DataSnapshot room : snapshot.getChildren()) {
                    if (room.child("members").hasChild(uid)) count++;
                }
                animateCounter(tvRoomCount, count,
                        n -> n + " Active Room" + (n != 1 ? "s" : ""));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // ── Stats: rooms joined, notes uploaded, messages sent ─────────────────────
    private void loadStats(String uid) {
        // Rooms
        roomsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int rooms = 0;
                for (DataSnapshot room : snapshot.getChildren()) {
                    if (room.child("members").hasChild(uid)) rooms++;
                }
                int finalRooms = rooms;
                animateCounter(tvStatRooms, finalRooms, n -> String.valueOf(n));
                animateCounter(tvSessionCount, finalRooms,
                        n -> n + " session" + (n != 1 ? "s" : ""));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        // Notes (count all notes where uploaderId == uid across all rooms)
        FirebaseDatabase.getInstance().getReference("Notes")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int notes = 0;
                        for (DataSnapshot room : snapshot.getChildren()) {
                            for (DataSnapshot note : room.getChildren()) {
                                String uploader = note.child("uploaderId").getValue(String.class);
                                if (uid.equals(uploader)) notes++;
                            }
                        }
                        animateCounter(tvStatNotes, notes, n -> String.valueOf(n));
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });

        // Messages
        FirebaseDatabase.getInstance().getReference("Messages")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int msgs = 0;
                        for (DataSnapshot room : snapshot.getChildren()) {
                            for (DataSnapshot msg : room.getChildren()) {
                                String sender = msg.child("senderId").getValue(String.class);
                                if (uid.equals(sender)) msgs++;
                            }
                        }
                        animateCounter(tvStatMessages, msgs, n -> String.valueOf(n));
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    // ── Recent rooms — last 3 rooms the user is a member of ────────────────────
    private void loadRecentRooms(String uid) {
        roomsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String[]> rooms = new ArrayList<>(); // [roomCode, roomName]
                for (DataSnapshot room : snapshot.getChildren()) {
                    if (room.child("members").hasChild(uid)) {
                        String code = room.getKey();
                        String name = room.child("roomName").getValue(String.class);
                        if (name == null || name.isEmpty()) name = code;
                        rooms.add(new String[]{code, name});
                    }
                }

                recentRoomsContainer.removeAllViews();

                if (rooms.isEmpty()) {
                    tvNoRecentRooms.setVisibility(View.VISIBLE);
                    return;
                }

                tvNoRecentRooms.setVisibility(View.GONE);

                // Show up to 3 most recent chips
                int limit = Math.min(rooms.size(), 3);
                for (int i = rooms.size() - 1; i >= rooms.size() - limit; i--) {
                    String[] room = rooms.get(i);
                    addRecentRoomChip(room[0], room[1]);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void addRecentRoomChip(String roomCode, String roomName) {
        // Build chip programmatically
        FrameLayout chip = new FrameLayout(this);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        chipParams.setMarginEnd((int) (10 * getResources().getDisplayMetrics().density));
        chip.setLayoutParams(chipParams);
        chip.setBackground(getDrawable(R.drawable.recent_room_chip_bg));
        chip.setClickable(true);
        chip.setFocusable(true);

        // Inner layout
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        int padV = (int) (10 * getResources().getDisplayMetrics().density);
        inner.setPadding(pad, padV, pad, padV);
        inner.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        // Room name
        TextView tvName = new TextView(this);
        tvName.setText(roomName);
        tvName.setTextColor(0xFFFFFFFF);
        tvName.setTextSize(13f);
        tvName.setTypeface(tvName.getTypeface(), android.graphics.Typeface.BOLD);
        tvName.setMaxLines(1);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvName.setMaxWidth((int) (140 * getResources().getDisplayMetrics().density));

        // Room code
        TextView tvCode = new TextView(this);
        tvCode.setText("# " + roomCode);
        tvCode.setTextColor(0xFF4CAF50);
        tvCode.setTextSize(10f);
        tvCode.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        ((LinearLayout.LayoutParams) tvCode.getLayoutParams()).topMargin =
                (int) (2 * getResources().getDisplayMetrics().density);

        inner.addView(tvName);
        inner.addView(tvCode);
        chip.addView(inner);

        // Click → open room directly
        chip.setOnClickListener(v -> {
            Intent intent = new Intent(this, StudyRoomInsideActivity.class);
            intent.putExtra("roomCode", roomCode);
            intent.putExtra("roomName", roomName);
            startActivity(intent);
        });

        // Entrance animation
        chip.setAlpha(0f);
        chip.setTranslationX(-20f);
        chip.animate().alpha(1f).translationX(0f)
                .setDuration(250).setStartDelay(50L * recentRoomsContainer.getChildCount())
                .start();

        recentRoomsContainer.addView(chip);
    }

    // ── Streak + sessions from SharedPreferences ───────────────────────────────
    // Streak = consecutive days the user opened the app
    private void loadStreakAndSessions(String uid) {
        SharedPreferences prefs = getSharedPreferences("studysync_prefs", MODE_PRIVATE);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date());
        String lastOpen = prefs.getString("last_open_date_" + uid, "");
        int streak = prefs.getInt("streak_" + uid, 0);
        int sessions = prefs.getInt("sessions_" + uid, 0);

        // Increment streak if this is a new day
        if (!today.equals(lastOpen)) {
            // Check if yesterday was the last open day (consecutive)
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -1);
            String yesterday = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(cal.getTime());
            if (yesterday.equals(lastOpen)) {
                streak++;
            } else if (!lastOpen.isEmpty()) {
                streak = 1; // streak broken
            } else {
                streak = 1; // first time
            }
            prefs.edit()
                    .putString("last_open_date_" + uid, today)
                    .putInt("streak_" + uid, streak)
                    .apply();
        }

        int finalStreak = streak;
        tvStreak.setText(finalStreak + " day" + (finalStreak != 1 ? "" : "") + " streak");
    }

    // ── Animated counter ───────────────────────────────────────────────────────
    interface CounterFormatter { String format(int n); }

    private void animateCounter(TextView tv, int target, CounterFormatter fmt) {
        ValueAnimator anim = ValueAnimator.ofInt(0, target);
        anim.setDuration(900);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.addUpdateListener(a -> tv.setText(fmt.format((int) a.getAnimatedValue())));
        anim.start();
    }

    // ── Entrance animation — stagger each section ──────────────────────────────
    private void animateEntrance() {
        int[] ids = {
                R.id.cardActiveRooms,
                R.id.cardCreateRoom,
                R.id.cardJoinRoom,
                R.id.cardTip
        };
        for (int i = 0; i < ids.length; i++) {
            View v = findViewById(ids[i]);
            if (v == null) continue;
            v.setAlpha(0f);
            v.setTranslationY(60f);
            v.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(500)
                    .setStartDelay(100L * i)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
    }

    // ── Card press animation ───────────────────────────────────────────────────
    private void animateCard(View card) {
        card.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80)
                .withEndAction(() ->
                        card.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                .start();
    }
}