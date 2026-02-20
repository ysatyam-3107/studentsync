package com.example.studysync;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvProfileName, tvProfileEmail;
    private ImageView imgProfilePhoto;
    private Button btnLogout;
    private CardView cardEditProfile;

    private FirebaseAuth auth;
    private DatabaseReference userRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileEmail = findViewById(R.id.tvProfileEmail);
        imgProfilePhoto = findViewById(R.id.imgProfilePhoto);
        btnLogout = findViewById(R.id.btnLogout);
        cardEditProfile = findViewById(R.id.cardEditProfile);

        auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            finish();
            return;
        }

        userRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(user.getUid());

        loadUserProfile();

        cardEditProfile.setOnClickListener(v -> showEditProfileBottomSheet());

        btnLogout.setOnClickListener(v -> {
            auth.signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void loadUserProfile() {

        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                if (snapshot.exists()) {

                    String name = snapshot.child("name").getValue(String.class);
                    String email = snapshot.child("email").getValue(String.class);
                    String photoUrl = snapshot.child("photoUrl").getValue(String.class);

                    tvProfileName.setText(name != null ? name : "User");
                    tvProfileEmail.setText(email != null ? email :
                            auth.getCurrentUser().getEmail());

                    if (photoUrl != null && !photoUrl.isEmpty()) {
                        Glide.with(ProfileActivity.this)
                                .load(photoUrl)
                                .into(imgProfilePhoto);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ProfileActivity.this,
                        "Failed to load profile",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditProfileBottomSheet() {

        BottomSheetDialog dialog =
                new BottomSheetDialog(this);

        View view = getLayoutInflater()
                .inflate(R.layout.bottom_sheet_edit_profile, null);

        EditText etEditName =
                view.findViewById(R.id.etEditName);

        Button btnSave =
                view.findViewById(R.id.btnSaveProfile);

        dialog.setContentView(view);
        dialog.show();

        btnSave.setOnClickListener(v -> {

            String newName =
                    etEditName.getText().toString().trim();

            if (TextUtils.isEmpty(newName)) {
                etEditName.setError("Name cannot be empty");
                return;
            }

            userRef.child("name").setValue(newName)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this,
                                "Profile Updated",
                                Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    });
        });
    }
}