package com.example.studysync;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.*;

import com.google.firebase.auth.*;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.*;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    // ── Views ──────────────────────────────────────────────────────────────────
    private com.google.android.material.textfield.TextInputEditText
            etName, etEmail, etPassword, etConfirmPassword;
    private Button      btnRegister;
    private TextView    tvLoginHere, tvPhotoHint, tvStrengthLabel;
    private ImageView   imgProfile;
    private ProgressBar progressBar, strengthBar;
    private LinearLayout successOverlay;

    // ── State ──────────────────────────────────────────────────────────────────
    private Uri selectedImageUri = null;

    // ── Firebase ───────────────────────────────────────────────────────────────
    private FirebaseAuth       auth;
    private DatabaseReference  usersRef;
    private StorageReference   storageRef;

    private ActivityResultLauncher<String> pickImageLauncher;

    // ──────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initViews();
        initFirebase();
        setupImagePicker();
        setupClickListeners();
        setupPasswordStrength();
        setupImeChaining();
    }

    // ── View wiring ────────────────────────────────────────────────────────────
    private void initViews() {
        etName            = findViewById(R.id.etName);
        etEmail           = findViewById(R.id.etEmailReg);
        etPassword        = findViewById(R.id.etPasswordReg);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister       = findViewById(R.id.btnRegisterNow);
        tvLoginHere       = findViewById(R.id.tvLoginHere);
        tvPhotoHint       = findViewById(R.id.tvPhotoHint);
        tvStrengthLabel   = findViewById(R.id.tvStrengthLabel);
        imgProfile        = findViewById(R.id.imgProfile);
        progressBar       = findViewById(R.id.progressBar);
        strengthBar       = findViewById(R.id.strengthBar);
        successOverlay    = findViewById(R.id.successOverlay);
    }

    private void initFirebase() {
        auth       = FirebaseAuth.getInstance();
        usersRef   = FirebaseDatabase.getInstance().getReference("Users");
        storageRef = FirebaseStorage.getInstance().getReference("profile_images");
    }

    // ── Image picker ───────────────────────────────────────────────────────────
    private void setupImagePicker() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        imgProfile.setImageURI(uri);

                        // Animate the photo in
                        imgProfile.setScaleX(0.8f);
                        imgProfile.setScaleY(0.8f);
                        imgProfile.animate()
                                .scaleX(1f).scaleY(1f)
                                .setDuration(300)
                                .setInterpolator(new OvershootInterpolator(2f))
                                .start();

                        // Update hint text
                        tvPhotoHint.setText("✅ Photo selected");
                        tvPhotoHint.setTextColor(0xFF4CAF50);
                    }
                }
        );
    }

    // ── Click listeners ────────────────────────────────────────────────────────
    private void setupClickListeners() {
        imgProfile.setOnClickListener(v -> {
            animateButton(imgProfile);
            pickImageLauncher.launch("image/*");
        });

        btnRegister.setOnClickListener(v -> {
            animateButton(btnRegister);
            btnRegister.postDelayed(this::registerUser, 150);
        });

        tvLoginHere.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    // ── IME keyboard chaining: Name → Email → Password → Confirm → Register ───
    private void setupImeChaining() {
        etName.setOnEditorActionListener((v, actionId, event) -> {
            etEmail.requestFocus();
            return true;
        });
        etEmail.setOnEditorActionListener((v, actionId, event) -> {
            etPassword.requestFocus();
            return true;
        });
        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            etConfirmPassword.requestFocus();
            return true;
        });
        etConfirmPassword.setOnEditorActionListener((v, actionId, event) -> {
            registerUser();
            return true;
        });
    }

    // ── Password strength meter ────────────────────────────────────────────────
    private void setupPasswordStrength() {
        etPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int strength = getPasswordStrength(s.toString());
                animateStrengthBar(strength);

                switch (strength) {
                    case 0:
                        tvStrengthLabel.setText("");
                        break;
                    case 1:
                        tvStrengthLabel.setText("Weak");
                        tvStrengthLabel.setTextColor(0xFFEF5350);
                        break;
                    case 2:
                        tvStrengthLabel.setText("Medium");
                        tvStrengthLabel.setTextColor(0xFFFF9800);
                        break;
                    case 3:
                        tvStrengthLabel.setText("Strong");
                        tvStrengthLabel.setTextColor(0xFF4CAF50);
                        break;
                }
            }
        });
    }

    /**
     * Returns 0–3:
     *  0 = empty
     *  1 = weak  (< 6 chars)
     *  2 = medium (6+ chars, no mixed case/numbers)
     *  3 = strong (8+ chars with upper+lower+digit or special char)
     */
    private int getPasswordStrength(String password) {
        if (password.isEmpty()) return 0;
        if (password.length() < 6) return 1;

        boolean hasUpper   = !password.equals(password.toLowerCase());
        boolean hasLower   = !password.equals(password.toUpperCase());
        boolean hasDigit   = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");

        int score = 0;
        if (hasUpper && hasLower) score++;
        if (hasDigit)             score++;
        if (hasSpecial)           score++;
        if (password.length() >= 8) score++;

        if (score >= 3) return 3;
        if (score >= 1) return 2;
        return 2; // 6+ chars but nothing extra → medium
    }

    private void animateStrengthBar(int target) {
        ObjectAnimator anim = ObjectAnimator.ofInt(strengthBar, "progress",
                strengthBar.getProgress(), target);
        anim.setDuration(300);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.start();
    }

    // ── Registration flow ──────────────────────────────────────────────────────
    private void registerUser() {
        String name     = etName.getText() != null
                ? etName.getText().toString().trim() : "";
        String email    = etEmail.getText() != null
                ? etEmail.getText().toString().trim().toLowerCase() : "";
        String password = etPassword.getText() != null
                ? etPassword.getText().toString() : "";
        String confirm  = etConfirmPassword.getText() != null
                ? etConfirmPassword.getText().toString() : "";

        if (!validateInputs(name, email, password, confirm)) return;

        setLoading(true);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && auth.getCurrentUser() != null) {
                        String uid = auth.getCurrentUser().getUid();
                        if (selectedImageUri != null) {
                            uploadProfileImage(uid, name, email);
                        } else {
                            saveUserToDatabase(uid, name, email, "");
                        }
                    } else {
                        setLoading(false);
                        handleAuthError(task.getException());
                    }
                });
    }

    // ── Validation ─────────────────────────────────────────────────────────────
    private boolean validateInputs(String name, String email,
                                   String password, String confirm) {
        // Name — min 2 real characters
        if (name.length() < 2) {
            etName.setError("Enter your full name (min 2 characters)");
            etName.requestFocus();
            return false;
        }

        // Email
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter a valid email address");
            etEmail.requestFocus();
            return false;
        }

        // Password length
        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return false;
        }

        // Confirm password match
        if (!password.equals(confirm)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            shakeView(etConfirmPassword);
            return false;
        }

        return true;
    }

    // ── Upload profile image ───────────────────────────────────────────────────
    private void uploadProfileImage(String uid, String name, String email) {
        StorageReference ref = storageRef.child(uid + ".jpg");
        ref.putFile(selectedImageUri)
                .addOnSuccessListener(snap ->
                        ref.getDownloadUrl().addOnSuccessListener(uri ->
                                saveUserToDatabase(uid, name, email, uri.toString())))
                .addOnFailureListener(e ->
                        // Image upload failed — still create the account without photo
                        saveUserToDatabase(uid, name, email, ""));
    }

    // ── Save to Realtime DB ────────────────────────────────────────────────────
    private void saveUserToDatabase(String uid, String name, String email, String photoUrl) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("name",      name);
        userData.put("email",     email);
        userData.put("photoUrl",  photoUrl);
        userData.put("createdAt", System.currentTimeMillis());
        userData.put("status",    "online");

        usersRef.child(uid).setValue(userData)
                .addOnSuccessListener(aVoid -> {
                    setLoading(false);
                    showSuccessAndNavigate();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this,
                            "Database error: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
        // Note: removed addOnCompleteListener for setLoading — now handled
        // explicitly in success/failure so it can never be missed
    }

    // ── Success animation → navigate ──────────────────────────────────────────
    private void showSuccessAndNavigate() {
        // Swap button for success overlay with animation
        btnRegister.setVisibility(View.GONE);
        successOverlay.setVisibility(View.VISIBLE);
        successOverlay.setScaleX(0.8f);
        successOverlay.setScaleY(0.8f);
        successOverlay.setAlpha(0f);
        successOverlay.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(350)
                .setInterpolator(new OvershootInterpolator(2f))
                .withEndAction(() ->
                        successOverlay.postDelayed(() -> {
                            startActivity(new Intent(this, MainActivity.class));
                            finish();
                        }, 900))
                .start();
    }

    // ── Auth error handling ────────────────────────────────────────────────────
    private void handleAuthError(Exception e) {
        if (e instanceof FirebaseAuthUserCollisionException) {
            etEmail.setError("This email is already registered");
            etEmail.requestFocus();
        } else if (e instanceof FirebaseAuthWeakPasswordException) {
            etPassword.setError("Password is too weak");
            etPassword.requestFocus();
        } else if (e != null) {
            Toast.makeText(this,
                    "Registration failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    // ── Loading state ──────────────────────────────────────────────────────────
    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!isLoading);
        btnRegister.setText(isLoading ? "" : "Create Account");
    }

    // ── Animations ─────────────────────────────────────────────────────────────
    private void animateButton(View view) {
        view.animate()
                .scaleX(0.94f).scaleY(0.94f)
                .setDuration(100)
                .withEndAction(() ->
                        view.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                .start();
    }

    private void shakeView(View v) {
        ObjectAnimator shake = ObjectAnimator.ofFloat(v, "translationX",
                0f, -14f, 14f, -10f, 10f, -6f, 6f, 0f);
        shake.setDuration(400);
        shake.start();
    }
}