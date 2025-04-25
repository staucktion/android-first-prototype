package com.example.staucktion;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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
import com.google.android.material.textview.MaterialTextView;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EmailLoginActivity extends AppCompatActivity {
    private static final String TAG = "EmailLogin";

    private TextInputEditText inputEmail, inputPassword;
    private MaterialButton    btnLogInEmail;
    private MaterialTextView  tvPromptRegister;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_login);

        inputEmail    = findViewById(R.id.inputEmail);
        inputPassword = findViewById(R.id.inputPassword);
        btnLogInEmail = findViewById(R.id.btnLogInEmail);
        tvPromptRegister = findViewById(R.id.tvPrompt);

        ApiService svc = RetrofitClient.getInstance().create(ApiService.class);

        btnLogInEmail.setOnClickListener(v -> {
            String email = inputEmail.getText().toString().trim();
            String pass  = inputPassword.getText().toString();
            if (!isValid(email, pass)) return;

            Log.d(TAG, "Attempting login for " + email);
            svc.loginWithEmail(new EmailAuthRequest(email, pass))
                    .enqueue(new Callback<AuthResponse>() {
                        @Override
                        public void onResponse(Call<AuthResponse> call,
                                               Response<AuthResponse> res) {
                            Log.d(TAG, "onResponse: code=" + res.code());
                            if (!res.isSuccessful() || res.body() == null) {
                                Log.d(TAG, "login failed or empty body");
                                Toast.makeText(EmailLoginActivity.this,
                                        "Login failed: " + res.code(),
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            String jwt = res.body().getToken();
                            long expiresIn = res.body().getExpiresInMillis();
                            Log.d(TAG, "login successful, token=" + jwt);
                            onAuthSuccess(jwt, expiresIn);
                        }

                        @Override
                        public void onFailure(Call<AuthResponse> call, Throwable t) {
                            Log.e(TAG, "network error during login", t);
                            Toast.makeText(EmailLoginActivity.this,
                                    "Network error: " + t.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        tvPromptRegister.setOnClickListener(v -> {
            // Launch your registration screen
            startActivity(new Intent(this, RegisterActivity.class));
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

    private void onAuthSuccess(String jwt, long expiresIn) {
        // 1) Persist token & expiry (expiresIn is in millis or seconds? adjust *1000L if seconds)
        long expiry = System.currentTimeMillis() + expiresIn * 1000L;
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit()
                .putString("appToken", jwt)
                .putLong("appTokenExpiry", expiry)
                .apply();

        // 2) Tell Retrofit to use it
        RetrofitClient.getInstance().setAuthToken(jwt);

        // 3) Toast & navigate to MainActivity, clearing the back-stack
        Toast.makeText(this,
                "Login successful!",
                Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();

        Log.d(TAG, "Stored expiry=" + expiry + " now=" + System.currentTimeMillis());
    }
}
