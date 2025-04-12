package com.example.staucktion;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import timber.log.Timber;

public class ProfileActivity extends AppCompatActivity {

    private ImageView profileImage;
    private TextView profileName, profileEmail;
    private GoogleSignInClient googleSignInClient;

    @SuppressLint("LogNotTimber")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile); // Your profile layout (activity_profile.xml)

        // Initialize views
        profileImage = findViewById(R.id.profileImage);
        profileName = findViewById(R.id.profileName);
        profileEmail = findViewById(R.id.profileEmail);
        Button btnHomePage = findViewById(R.id.btnHomePage);
        Button btnLogout = findViewById(R.id.btnLogout);

        // Configure Google Sign-In options and get the client.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // Load the profile data (user name, email, photo URL) from SharedPreferences.
        loadUserProfile();

        // Button to go back to MainActivity (if needed)
        btnHomePage.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });

        // Logout button click listener.
        btnLogout.setOnClickListener(v -> {
            Log.d("ProfileActivity", "Logout button clicked");
            // Clear user data from SharedPreferences.
            SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            prefs.edit().clear().apply();

            // Sign out from Google.
            googleSignInClient.signOut().addOnCompleteListener(task -> {
                Toast.makeText(ProfileActivity.this, "Logged out", Toast.LENGTH_SHORT).show();
                // Redirect to LoginActivity after logout.
                Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
                startActivity(intent);
                finish();
            });
        });
    }

    private void loadUserProfile() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String name = prefs.getString("userName", "User Name");
        String email = prefs.getString("userEmail", "user@example.com");
        String photoUrl = prefs.getString("userPhotoUrl", "");

        profileName.setText(name);
        profileEmail.setText(email);

        // If there is a photo URL, load it with Glide; otherwise, use a default placeholder.
        if (!photoUrl.isEmpty()) {
            Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.default_profile)
                    .into(profileImage);
        } else {
            profileImage.setImageResource(R.drawable.default_profile);
        }

        Timber.i("User profile loaded: %s, %s", name, email);
    }
}
