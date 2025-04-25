package com.example.staucktion;

import android.annotation.SuppressLint;
import android.content.Intent;
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
        long expiryMillis = extractExpiryFromJwt(jwt);
        if (expiryMillis <= 0) {
            // fallback: 24 h
            expiryMillis = System.currentTimeMillis() + 24 * 60 * 60 * 1000L;
            Log.w(TAG, "JWT had no exp claim, using 24h fallback");
        }

        Log.d(TAG, "storing expiry=" + expiryMillis + "  now=" + System.currentTimeMillis());
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit()
                .putString("appToken", jwt)
                .putLong("appTokenExpiry", expiryMillis)
                .apply();

        // tell Retrofit to use it
        RetrofitClient.getInstance().setAuthToken(jwt);

        Toast.makeText(this,
                "Login successful!",
                Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
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