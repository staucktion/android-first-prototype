package com.example.staucktion;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.example.staucktion.api.ApiService;
import com.example.staucktion.api.RetrofitClient;
import com.example.staucktion.models.AuthRequest;
import com.example.staucktion.models.AuthResponse;
import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * LoginActivity handles Google Sign-In and, if the user is already signed in,
 * immediately navigates to MainActivity.
 */
public class LoginActivity extends AppCompatActivity {
    private static final int RC_SIGN_IN = 1001;
    private GoogleSignInClient googleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Configure Google Sign-In options with your Web Client ID.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.web_client_id)) // from your strings.xml
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // Check if the user is already signed in.
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            // User is already logged in, so directly proceed to MainActivity.
            goToMainActivity();
            return;
        }

        // Set up the login button
        Button btnLogin = findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(v -> signIn());
    }

    private void signIn() {
        // Önce mevcut oturum açmış hesabı temizle
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            // Hesap temizlendikten sonra Google Sign-In intent'ini başlat
            Intent signInIntent = googleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });
    }

    /**
     * Handle the result from the Google sign-in activity.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            if (data == null) {
                Log.w("LoginActivity", "Received null data in onActivityResult");
                return;
            }
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    // We have a valid Google account
                    String idToken = account.getIdToken();

                    // Save user details in SharedPreferences
                    SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    // This can be replaced later once you verify the token on your backend
                    editor.putString("appToken", "TOKEN_FROM_BACKEND_IF_ANY");
                    editor.putString("userName", account.getDisplayName());
                    editor.putString("userEmail", account.getEmail());
                    if (account.getPhotoUrl() != null) {
                        editor.putString("userPhotoUrl", account.getPhotoUrl().toString());
                    } else {
                        editor.putString("userPhotoUrl", "");
                    }
                    editor.apply();

                    Log.d("LoginActivity", "Google sign-in successful, idToken: " + idToken);
                    Toast.makeText(LoginActivity.this, "Log in successful", Toast.LENGTH_SHORT).show();

                    // Optionally send the token to your backend for further authentication.
                    sendTokenToBackend(idToken);
                }
            } catch (ApiException e) {
                Log.w("LoginActivity", "Google sign in failed", e);
                Toast.makeText(LoginActivity.this, "Log in failed", Toast.LENGTH_SHORT).show();

            }
        }
    }

    /**
     * Optionally send the ID token to your backend for authentication.
     * If you want to skip backend verification, remove or comment out this method call.
     */
    private void sendTokenToBackend(String idToken) {
        // Create an instance of ApiService using your RetrofitClient
        ApiService apiService = RetrofitClient.getInstance().create(ApiService.class);
        AuthRequest authRequest = new AuthRequest(idToken);

        // Example endpoint call to /auth/google
        Call<AuthResponse> call = apiService.loginWithGoogle(authRequest);
        call.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // If the backend returns an app token, store it
                    String appToken = response.body().getToken();
                    SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                    prefs.edit().putString("appToken", appToken).apply();
                    Log.d("LoginActivity", "Backend authentication successful, appToken: " + appToken);
                } else {
                    Log.w("LoginActivity", "Backend authentication failed, code: " + response.code());
                }
                // Whether success or failure, proceed to MainActivity
                goToMainActivity();
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Log.e("LoginActivity", "Backend authentication error", t);
                // On failure, still proceed to MainActivity or handle accordingly
                goToMainActivity();
            }
        });
    }

    /**
     * Navigate to MainActivity and finish LoginActivity so user can't go back.
     */
    private void goToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
