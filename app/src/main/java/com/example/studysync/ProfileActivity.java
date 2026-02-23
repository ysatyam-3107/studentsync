package com.example.studysync;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileActivity extends AppCompatActivity {

    private CircleImageView ivProfileImage;
    private ImageView ivEditPhoto;
    private TextView tvProfileName, tvProfileEmail;
    private Button btnLogout;
    private ProgressBar progressBar;

    private FirebaseAuth auth;
    private DatabaseReference userRef;

    private Uri selectedImageUri;
    private String currentName = "";

    private ActivityResultLauncher<String> imagePicker;

    private static final String UPLOAD_PRESET = "studysync_profiles";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initViews();
        initFirebase();
        setupImagePicker();
        loadProfile();
        setupClicks();
    }

    private void initViews() {
        ivProfileImage = findViewById(R.id.ivProfileImage);
        ivEditPhoto = findViewById(R.id.ivEditPhoto);
        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileEmail = findViewById(R.id.tvProfileEmail);
        btnLogout = findViewById(R.id.btnLogout);
        progressBar = findViewById(R.id.progressBarProfile);
    }

    private void initFirebase() {
        auth = FirebaseAuth.getInstance();
        String uid = auth.getCurrentUser().getUid();
        userRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(uid);
    }

    private void setupImagePicker() {
        imagePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        uploadToCloudinary();
                    }
                });
    }

    private void setupClicks() {

        tvProfileName.setOnClickListener(v -> showEditNameDialog());

        ivProfileImage.setOnClickListener(v ->
                imagePicker.launch("image/*"));

        ivEditPhoto.setOnClickListener(v ->
                imagePicker.launch("image/*"));

        btnLogout.setOnClickListener(v -> {
            auth.signOut();
            finish();
        });
    }

    private void loadProfile() {
        progressBar.setVisibility(View.VISIBLE);

        userRef.addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {

                        progressBar.setVisibility(View.GONE);

                        currentName = snapshot.child("name")
                                .getValue(String.class);

                        String email = snapshot.child("email")
                                .getValue(String.class);

                        String photoUrl = snapshot.child("photoUrl")
                                .getValue(String.class);

                        tvProfileName.setText(
                                currentName != null ?
                                        currentName : "User");

                        tvProfileEmail.setText(
                                email != null ?
                                        email :
                                        auth.getCurrentUser().getEmail());

                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            Glide.with(ProfileActivity.this)
                                    .load(photoUrl)
                                    .into(ivProfileImage);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressBar.setVisibility(View.GONE);
                    }
                });
    }

    private void showEditNameDialog() {

        View view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_edit_name, null);

        EditText etNewName = view.findViewById(R.id.etNewName);
        etNewName.setText(currentName);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        view.findViewById(R.id.btnSaveName)
                .setOnClickListener(v -> {

                    String newName = etNewName.getText()
                            .toString()
                            .trim();

                    if (TextUtils.isEmpty(newName)) {
                        etNewName.setError("Enter valid name");
                        return;
                    }

                    userRef.child("name")
                            .setValue(newName)
                            .addOnSuccessListener(aVoid -> {
                                tvProfileName.setText(newName);
                                currentName = newName;
                                dialog.dismiss();
                                Toast.makeText(this,
                                        "Name updated",
                                        Toast.LENGTH_SHORT).show();
                            });
                });

        view.findViewById(R.id.btnCancelName)
                .setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void uploadToCloudinary() {

        progressBar.setVisibility(View.VISIBLE);

        MediaManager.get().upload(selectedImageUri)
                .unsigned("studysync_profiles")
                .callback(new UploadCallback() {

                    @Override
                    public void onStart(String requestId) { }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) { }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {

                        String imageUrl = resultData.get("secure_url").toString();

                        userRef.child("photoUrl").setValue(imageUrl);

                        Glide.with(ProfileActivity.this)
                                .load(imageUrl)
                                .into(ivProfileImage);

                        progressBar.setVisibility(View.GONE);

                        Toast.makeText(ProfileActivity.this,
                                "Photo updated",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String requestId, com.cloudinary.android.callback.ErrorInfo error) {

                        progressBar.setVisibility(View.GONE);

                        Toast.makeText(ProfileActivity.this,
                                "Upload failed: " + error.getDescription(),
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onReschedule(String requestId, com.cloudinary.android.callback.ErrorInfo error) { }

                });}}