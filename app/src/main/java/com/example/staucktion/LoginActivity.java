package com.example.staucktion;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.staucktion.api.ApiService;
import com.example.staucktion.api.RetrofitClient;
import com.example.staucktion.models.AuthRequest;
import com.example.staucktion.models.AuthResponse;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

public class LoginActivity extends AppCompatActivity {
    private static final int RC_SIGN_IN = 1001;
    private GoogleSignInClient googleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Configure Google Sign-In options with your Web Client ID.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // Check if the user is already signed in.
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            // User is already logged in, proceed to MainActivity.
            goToMainActivity();
            return;
        }

        // Set up the login button.
        Button btnLogin = findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(v -> signIn());
    }

    private void signIn() {
        // Sign out to force account chooser every time.
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });
    }

    @SuppressLint("LogNotTimber")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            if (data == null) {
                Timber.w("Received null data in onActivityResult");
                return;
            }
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    // We have a valid Google account.
                    String idToken = account.getIdToken();

                    // Save user details in SharedPreferences.
                    SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("userName", account.getDisplayName());
                    editor.putString("userEmail", account.getEmail());
                    editor.putString("userPhotoUrl", account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : "");
                    editor.apply();

                    Timber.d("Google sign-in successful, idToken: %s", idToken);
                    Toast.makeText(LoginActivity.this, "Log in successful", Toast.LENGTH_SHORT).show();

                    // Send the token to your backend for further authentication.
                    sendTokenToBackend(idToken);
                }
            } catch (ApiException e) {
                Timber.w(e, "Google sign in failed");
                Toast.makeText(LoginActivity.this, "Log in failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Send the ID token to your backend for authentication.
     * On a successful response, store the returned app token and its expiry.
     */
    private void sendTokenToBackend(String idToken) {
        ApiService apiService = RetrofitClient.getInstance().create(ApiService.class);
        Call<AuthResponse> call = apiService.loginWithGoogle(new AuthRequest(idToken));

        call.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    String jwtToken = authResponse.getToken();

                    // Compute the expiry timestamp.
                    long expiresInMillis = authResponse.getExpiresInMillis(); // e.g., 3600000 for 1 hour.
                    if (expiresInMillis == 0) {
                        // Fallback to a default expiry time, e.g., 1 hour.
                        expiresInMillis = 3600000;
                    }
                    long expiryTimestamp = System.currentTimeMillis() + expiresInMillis;

                    // Log the received token and computed expiry.
                    Timber.d("Received token: %s, expires in: %d ms", jwtToken, expiresInMillis);
                    Timber.d("Authentication response: %s", authResponse.toString());

                    // Save the token and expiry in SharedPreferences.
                    SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                    prefs.edit()
                            .putString("appToken", jwtToken)
                            .putLong("appTokenExpiry", expiryTimestamp)
                            .apply();

                    // Set the token on the Retrofit client for future API calls.
                    RetrofitClient.getInstance().setAuthToken(jwtToken);

                    Toast.makeText(LoginActivity.this, "Log in successful", Toast.LENGTH_SHORT).show();
                    goToMainActivity();
                } else {
                    Toast.makeText(LoginActivity.this, "Login failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Timber.e(t, "Network or server error");
            }
        });
    }

    /**
    /**
     * Navigate to MainActivity and finish LoginActivity so the user cannot navigate back.
     */
    private void goToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}