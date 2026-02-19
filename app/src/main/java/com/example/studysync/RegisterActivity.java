package com.example.studysync;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.*;

import com.google.firebase.auth.*;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.*;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText etName, etEmail, etPassword;
    private Button btnRegister;
    private TextView tvLoginHere;
    private ImageView imgProfile;
    private ProgressBar progressBar;

    private Uri selectedImageUri;

    private FirebaseAuth auth;
    private DatabaseReference usersRef;
    private StorageReference storageRef;

    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initViews();
        initFirebase();
        setupImagePicker();
        setupClickListeners();
    }

    private void initViews() {
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmailReg);
        etPassword = findViewById(R.id.etPasswordReg);
        btnRegister = findViewById(R.id.btnRegisterNow);
        tvLoginHere = findViewById(R.id.tvLoginHere);
        imgProfile = findViewById(R.id.imgProfile);
        progressBar = findViewById(R.id.progressBar); // Add this in XML
    }

    private void initFirebase() {
        auth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("Users");
        storageRef = FirebaseStorage.getInstance().getReference("profile_images");
    }

    private void setupImagePicker() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        imgProfile.setImageURI(uri);
                    }
                }
        );
    }

    private void setupClickListeners() {
        imgProfile.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        btnRegister.setOnClickListener(v -> registerUser());

        tvLoginHere.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void registerUser() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (!validateInputs(name, email, password)) return;

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

    private boolean validateInputs(String name, String email, String password) {

        if (TextUtils.isEmpty(name)) {
            etName.setError("Name is required");
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter valid email");
            return false;
        }

        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            return false;
        }

        return true;
    }

    private void uploadProfileImage(String uid, String name, String email) {
        StorageReference ref = storageRef.child(uid + ".jpg");

        ref.putFile(selectedImageUri)
                .addOnSuccessListener(taskSnapshot ->
                        ref.getDownloadUrl().addOnSuccessListener(uri ->
                                saveUserToDatabase(uid, name, email, uri.toString())
                        )
                )
                .addOnFailureListener(e ->
                        saveUserToDatabase(uid, name, email, "")
                );
    }

    private void saveUserToDatabase(String uid, String name, String email, String photoUrl) {

        Map<String, Object> userData = new HashMap<>();
        userData.put("name", name);
        userData.put("email", email);
        userData.put("photoUrl", photoUrl);
        userData.put("createdAt", System.currentTimeMillis());
        userData.put("status", "online");

        usersRef.child(uid).setValue(userData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Registration Successful 🎉", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Database error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                )
                .addOnCompleteListener(task -> setLoading(false));
    }

    private void handleAuthError(Exception e) {
        if (e instanceof FirebaseAuthUserCollisionException) {
            etEmail.setError("Email already registered");
        } else if (e instanceof FirebaseAuthWeakPasswordException) {
            etPassword.setError("Weak password");
        } else {
            Toast.makeText(this, "Registration failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!isLoading);
        btnRegister.setText(isLoading ? "Registering..." : "Register");
    }
}
