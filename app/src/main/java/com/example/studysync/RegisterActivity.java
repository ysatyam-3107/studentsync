package com.example.studysync;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.FirebaseStorage;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private EditText etName, etEmail, etPassword;
    private Button btnRegister;
    private TextView tvLoginHere;
    private ImageView imgProfile;

    private Uri selectedImageUri = null;

    private FirebaseAuth auth;
    private DatabaseReference usersRef;
    private StorageReference storageRef;

    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmailReg);
        etPassword = findViewById(R.id.etPasswordReg);
        btnRegister = findViewById(R.id.btnRegisterNow);
        tvLoginHere = findViewById(R.id.tvLoginHere);
        imgProfile = findViewById(R.id.imgProfile);

        auth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("Users");
        storageRef = FirebaseStorage.getInstance().getReference("profile_images");

        Log.d(TAG, "Firebase Database Reference: " + usersRef.toString());

        // Register image picker
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri result) {
                        if (result != null) {
                            selectedImageUri = result;
                            imgProfile.setImageURI(result);
                        }
                    }
                }
        );

        imgProfile.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        btnRegister.setOnClickListener(v -> registerUser());

        tvLoginHere.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void registerUser() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        // Validation
        if (name.isEmpty()) {
            etName.setError("Name is required");
            etName.requestFocus();
            return;
        }

        if (email.isEmpty()) {
            etEmail.setError("Email is required");
            etEmail.requestFocus();
            return;
        }

        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }

        btnRegister.setEnabled(false);
        btnRegister.setText("Registering...");

        Log.d(TAG, "Starting registration for: " + email);

        // Create user with Firebase Auth
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && auth.getCurrentUser() != null) {
                        String uid = auth.getCurrentUser().getUid();
                        Log.d(TAG, "✅ User created in Auth with UID: " + uid);

                        // Upload image if selected, otherwise save without image
                        if (selectedImageUri != null) {
                            uploadProfileImageAndSaveUser(uid, name, email, selectedImageUri);
                        } else {
                            saveUserToDatabase(uid, name, email, "");
                        }
                    } else {
                        btnRegister.setEnabled(true);
                        btnRegister.setText("Register");
                        String err = (task.getException() != null) ?
                                task.getException().getMessage() : "Registration failed";
                        Log.e(TAG, "❌ Auth registration failed: " + err);
                        Toast.makeText(RegisterActivity.this,
                                "Error: " + err, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void uploadProfileImageAndSaveUser(String uid, String name, String email, Uri imageUri) {
        Log.d(TAG, "Uploading profile image for UID: " + uid);

        StorageReference ref = storageRef.child(uid + ".jpg");
        ref.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    Log.d(TAG, "✅ Image uploaded successfully");
                    ref.getDownloadUrl()
                            .addOnSuccessListener(uri -> {
                                String photoUrl = uri.toString();
                                Log.d(TAG, "✅ Got download URL: " + photoUrl);
                                saveUserToDatabase(uid, name, email, photoUrl);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "❌ Failed to get download URL: " + e.getMessage());
                                // Still save user without photo
                                saveUserToDatabase(uid, name, email, "");
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Image upload failed: " + e.getMessage());
                    // Still save user without photo
                    saveUserToDatabase(uid, name, email, "");
                    Toast.makeText(RegisterActivity.this,
                            "Image upload failed, continuing without photo", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveUserToDatabase(String uid, String name, String email, String photoUrl) {
        Log.d(TAG, "Saving user to database...");
        Log.d(TAG, "UID: " + uid);
        Log.d(TAG, "Name: " + name);
        Log.d(TAG, "Email: " + email);
        Log.d(TAG, "Photo URL: " + photoUrl);

        Map<String, Object> userData = new HashMap<>();
        userData.put("name", name);
        userData.put("email", email);
        userData.put("photoUrl", photoUrl);

        usersRef.child(uid).setValue(userData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ User data saved to database successfully!");
                    Toast.makeText(RegisterActivity.this,
                            "Registration successful!", Toast.LENGTH_SHORT).show();

                    btnRegister.setEnabled(true);
                    btnRegister.setText("Register");

                    // Navigate to MainActivity
                    startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Database save failed: " + e.getMessage());
                    e.printStackTrace();

                    btnRegister.setEnabled(true);
                    btnRegister.setText("Register");

                    Toast.makeText(RegisterActivity.this,
                            "Failed to save user data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}