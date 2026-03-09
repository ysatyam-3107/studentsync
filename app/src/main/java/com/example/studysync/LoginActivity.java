package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.*;

import com.google.firebase.auth.*;
import com.google.firebase.database.*;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    // ── Views ──────────────────────────────────────────────────────────────────
    private com.google.android.material.textfield.TextInputEditText
            etEmail, etPassword;
    private Button       btnLogin;
    private TextView     tvRegisterHere, tvForgotPassword, tvErrorMessage;
    private ProgressBar  progressBar;
    private LinearLayout errorBanner, successOverlay, logoSection;
    private androidx.cardview.widget.CardView loginCard;

    // ── Firebase ───────────────────────────────────────────────────────────────
    private FirebaseAuth      auth;
    private DatabaseReference usersRef;

    // ──────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initViews();
        initFirebase();
        setupClickListeners();
        setupImeChaining();
        playEntranceAnimation();
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current != null) checkAndCreateUserData(current);
    }

    // ── View wiring ────────────────────────────────────────────────────────────
    private void initViews() {
        etEmail          = findViewById(R.id.etEmailLogin);
        etPassword       = findViewById(R.id.etPasswordLogin);
        btnLogin         = findViewById(R.id.btnLogin);
        tvRegisterHere   = findViewById(R.id.tvRegisterHere);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvErrorMessage   = findViewById(R.id.tvErrorMessage);
        progressBar      = findViewById(R.id.progressBar);
        errorBanner      = findViewById(R.id.errorBanner);
        successOverlay   = findViewById(R.id.successOverlay);
        logoSection      = findViewById(R.id.logoSection);
        loginCard        = findViewById(R.id.loginCard);
    }

    private void initFirebase() {
        auth     = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("Users");
    }

    // ── Click listeners ────────────────────────────────────────────────────────
    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> {
            animateButton(btnLogin);
            hideError();
            btnLogin.postDelayed(this::attemptLogin, 150);
        });

        tvRegisterHere.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
            overridePendingTransition(
                    android.R.anim.slide_in_left,
                    android.R.anim.slide_out_right);
        });

        tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
    }

    // ── IME chaining: Email → Password → submit ────────────────────────────────
    private void setupImeChaining() {
        etEmail.setOnEditorActionListener((v, actionId, event) -> {
            etPassword.requestFocus();
            return true;
        });
        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            hideError();
            attemptLogin();
            return true;
        });
    }

    // ── Login logic ────────────────────────────────────────────────────────────
    private void attemptLogin() {
        String email    = etEmail.getText() != null
                ? etEmail.getText().toString().trim().toLowerCase() : "";
        String password = etPassword.getText() != null
                ? etPassword.getText().toString() : "";

        if (TextUtils.isEmpty(email)) {
            showError("Please enter your email address");
            shakeView(etEmail); return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email address");
            shakeView(etEmail); return;
        }
        if (TextUtils.isEmpty(password)) {
            showError("Please enter your password");
            shakeView(etPassword); return;
        }
        if (password.length() < 6) {
            showError("Password must be at least 6 characters");
            shakeView(etPassword); return;
        }

        setLoading(true);

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && auth.getCurrentUser() != null) {
                        checkAndCreateUserData(auth.getCurrentUser());
                    } else {
                        setLoading(false);
                        handleAuthError(task.getException());
                    }
                });
    }

    // ── Check / create user record ─────────────────────────────────────────────
    private void checkAndCreateUserData(FirebaseUser user) {
        usersRef.child(user.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) createUserData(user);
                        else                    onLoginSuccess();
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "DB check error: " + error.getMessage());
                        onLoginSuccess(); // don't block user on DB error
                    }
                });
    }

    private void createUserData(FirebaseUser user) {
        String uid   = user.getUid();
        String email = user.getEmail() != null ? user.getEmail() : "";
        String name  = user.getDisplayName();
        if (TextUtils.isEmpty(name))
            name = !email.isEmpty() ? email.split("@")[0] : "User";

        Map<String, Object> userData = new HashMap<>();
        userData.put("name",      name);
        userData.put("email",     email);
        userData.put("photoUrl",  "");
        userData.put("createdAt", System.currentTimeMillis());
        userData.put("status",    "online");

        usersRef.child(uid).setValue(userData)
                .addOnSuccessListener(v  -> onLoginSuccess())
                .addOnFailureListener(e  -> {
                    Log.e(TAG, "Failed to create user data: " + e.getMessage());
                    onLoginSuccess(); // proceed anyway
                });
    }

    // ── Success flow ───────────────────────────────────────────────────────────
    private void onLoginSuccess() {
        setLoading(false);
        showSuccessAndNavigate();
    }

    private void showSuccessAndNavigate() {
        btnLogin.setVisibility(View.GONE);
        successOverlay.setVisibility(View.VISIBLE);
        successOverlay.setAlpha(0f);
        successOverlay.setScaleX(0.85f);
        successOverlay.setScaleY(0.85f);
        successOverlay.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(350)
                .setInterpolator(new OvershootInterpolator(2f))
                .withEndAction(() ->
                        successOverlay.postDelayed(() -> {
                            startActivity(new Intent(this, MainActivity.class));
                            finish();
                            overridePendingTransition(
                                    android.R.anim.fade_in,
                                    android.R.anim.fade_out);
                        }, 800))
                .start();
    }

    // ── Forgot password ────────────────────────────────────────────────────────
    private void showForgotPasswordDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_forgot_password, null);
        com.google.android.material.textfield.TextInputEditText etResetEmail =
                view.findViewById(R.id.etResetEmail);

        // Pre-fill from the email field
        String current = etEmail.getText() != null
                ? etEmail.getText().toString().trim() : "";
        if (!current.isEmpty()) etResetEmail.setText(current);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setView(view)
                .setTitle("Reset Password")
                .setPositiveButton("Send Reset Link", null)
                .setNegativeButton("Cancel", null)
                .create();

        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dark_dialog_bg);

        dialog.show();

        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String resetEmail = etResetEmail.getText() != null
                    ? etResetEmail.getText().toString().trim() : "";
            if (!Patterns.EMAIL_ADDRESS.matcher(resetEmail).matches()) {
                etResetEmail.setError("Enter a valid email address");
                return;
            }
            auth.sendPasswordResetEmail(resetEmail)
                    .addOnSuccessListener(unused -> {
                        dialog.dismiss();
                        // Show success in the error banner (reused as info banner)
                        tvErrorMessage.setText("✅ Reset link sent to " + resetEmail);
                        tvErrorMessage.setTextColor(0xFF4CAF50);
                        errorBanner.setVisibility(View.VISIBLE);
                    })
                    .addOnFailureListener(e ->
                            etResetEmail.setError("Failed: " + e.getMessage()));
        });
    }

    // ── Auth error mapping ─────────────────────────────────────────────────────
    private void handleAuthError(Exception e) {
        if (e == null) { showError("Login failed. Please try again."); return; }

        if (e instanceof FirebaseAuthInvalidUserException) {
            showError("No account found with this email.");
            shakeView(etEmail);
        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
            showError("Incorrect password. Please try again.");
            shakeView(etPassword);
        } else if (e.getMessage() != null
                && e.getMessage().toLowerCase().contains("network")) {
            showError("No internet connection. Check your network.");
        } else {
            showError("Login failed: " + e.getMessage());
        }
    }

    // ── Error banner ───────────────────────────────────────────────────────────
    private void showError(String message) {
        tvErrorMessage.setText(message);
        tvErrorMessage.setTextColor(0xFFFFCDD2);
        errorBanner.setVisibility(View.VISIBLE);
        errorBanner.setTranslationY(-20f);
        errorBanner.setAlpha(0f);
        errorBanner.animate()
                .translationY(0f).alpha(1f)
                .setDuration(250)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    private void hideError() {
        errorBanner.setVisibility(View.GONE);
    }

    // ── Loading state ──────────────────────────────────────────────────────────
    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? "" : "Sign In");
        etEmail.setEnabled(!loading);
        etPassword.setEnabled(!loading);
    }

    // ── Entrance animation ─────────────────────────────────────────────────────
    private void playEntranceAnimation() {
        // Logo drops in with overshoot
        if (logoSection != null) {
            logoSection.setAlpha(0f);
            logoSection.setTranslationY(-50f);
            logoSection.animate()
                    .alpha(1f).translationY(0f)
                    .setDuration(550)
                    .setStartDelay(80)
                    .setInterpolator(new OvershootInterpolator(1.3f))
                    .start();
        }
        // Card rises from bottom
        if (loginCard != null) {
            loginCard.setAlpha(0f);
            loginCard.setTranslationY(70f);
            loginCard.animate()
                    .alpha(1f).translationY(0f)
                    .setDuration(500)
                    .setStartDelay(220)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
        // Register link fades in last
        if (tvRegisterHere != null) {
            tvRegisterHere.setAlpha(0f);
            tvRegisterHere.animate()
                    .alpha(1f)
                    .setDuration(400)
                    .setStartDelay(450)
                    .start();
        }
    }

    // ── Animations ─────────────────────────────────────────────────────────────
    private void animateButton(View view) {
        ObjectAnimator sx = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(sx, sy);
        set.setDuration(200);
        set.setInterpolator(new OvershootInterpolator(2f));
        set.start();
    }

    private void shakeView(View v) {
        ObjectAnimator.ofFloat(v, "translationX",
                        0f, -12f, 12f, -8f, 8f, -4f, 4f, 0f)
                .setDuration(400)
                .start();
    }
}