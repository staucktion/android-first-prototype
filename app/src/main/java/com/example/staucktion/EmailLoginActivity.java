package com.example.staucktion;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.util.Patterns;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.staucktion.api.ApiService;
import com.example.staucktion.api.RetrofitClient;
import com.example.staucktion.models.AuthResponse;
import com.example.staucktion.models.EmailAuthRequest;
import com.example.staucktion.models.UserInfoResponse;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EmailLoginActivity extends AppCompatActivity {
    private static final String TAG = "EmailLogin";
    private TextInputEditText inputEmail, inputPassword;
    private MaterialButton     btnLogInEmail;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_login);

        inputEmail    = findViewById(R.id.inputEmail);
        inputPassword = findViewById(R.id.inputPassword);
        btnLogInEmail = findViewById(R.id.btnLogInEmail);

        ApiService svc = RetrofitClient.getInstance().create(ApiService.class);

        btnLogInEmail.setOnClickListener(v -> {
            String email = inputEmail.getText().toString().trim();
            String pass  = inputPassword.getText().toString();
            if (!isValid(email, pass)) return;

            svc.loginWithEmail(new EmailAuthRequest(email, pass))
                    .enqueue(new Callback<AuthResponse>() {
                        @Override
                        public void onResponse(Call<AuthResponse> call,
                                               Response<AuthResponse> res) {
                            Log.d(TAG, "login response code=" + res.code());
                            if (!res.isSuccessful() || res.body() == null) {
                                Toast.makeText(EmailLoginActivity.this,
                                        "Login failed: " + res.code(),
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            String jwt = res.body().getToken();
                            onAuthSuccess(jwt);
                        }
                        @Override
                        public void onFailure(Call<AuthResponse> call, Throwable t) {
                            Log.e(TAG, "network error", t);
                            Toast.makeText(EmailLoginActivity.this,
                                    "Network error: " + t.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }

    private boolean isValid(String email, String pass) {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            inputEmail.setError("Invalid email");
            return false;
        }
        if (pass.length() < 6) {
            inputPassword.setError("Password too short");
            return false;
        }
        return true;
    }

    private void onAuthSuccess(String jwt) {
        // 1) Decode & store expiry (same as you have now)
        long expiryMillis = extractExpiryFromJwt(jwt);
        if (expiryMillis <= 0) {
            expiryMillis = System.currentTimeMillis() + 24 * 60 * 60 * 1000L;  // fallback 24h
        }
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit()
                .putString("appToken", jwt)
                .putLong("appTokenExpiry", expiryMillis)
                .apply();

        // 2) Tell Retrofit to use this token
        RetrofitClient.getInstance().setAuthToken(jwt);

        // 3) Now fetch the user’s profile
        ApiService svc = RetrofitClient.getInstance().create(ApiService.class);
        svc.getUserInfo().enqueue(new Callback<UserInfoResponse>() {
            @Override
            public void onResponse(Call<UserInfoResponse> call,
                                   Response<UserInfoResponse> res) {
                if (res.isSuccessful() && res.body()!=null
                        && res.body().getUser()!=null) {
                    // Extract name & photo URL
                    String first = res.body().getUser().getFirstName();
                    String last  = res.body().getUser().getLastName();
                    String fullName = first + " " + last;
                    String photoUrl = res.body().getUser().getPhotoUrl();

                    // Save them
                    prefs.edit()
                            .putString("userName", fullName)
                            .putString("userPhotoUrl", photoUrl != null ? photoUrl : "")
                            .apply();
                }
                // 4) Finally navigate to the main screen, clearing the back-stack
                Intent intent = new Intent(EmailLoginActivity.this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onFailure(Call<UserInfoResponse> call, Throwable t) {
                // Even if profile fetch fails, still go on:
                Intent intent = new Intent(EmailLoginActivity.this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });
    }

    /**
     * JWT payload is Base64-URL; decode, parse JSON, read "exp" (seconds) → ms
     */
    private long extractExpiryFromJwt(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length != 3) return -1;
            byte[] decoded = Base64.decode(parts[1], Base64.URL_SAFE|Base64.NO_PADDING|Base64.NO_WRAP);
            String payload = new String(decoded, "UTF-8");
            JSONObject obj = new JSONObject(payload);
            if (!obj.has("exp")) return -1;
            long expSeconds = obj.getLong("exp");
            return expSeconds * 1000L;
        } catch (Exception e) {
            Log.e(TAG, "failed to parse JWT exp", e);
            return -1;
        }
    }
}