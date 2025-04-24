// src/main/java/com/example/staucktion/EmailLoginActivity.java
package com.example.staucktion;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.staucktion.api.ApiService;
import com.example.staucktion.api.RetrofitClient;
import com.example.staucktion.models.AuthResponse;
import com.example.staucktion.models.EmailAuthRequest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EmailLoginActivity extends AppCompatActivity {
    private TextInputEditText inputEmail, inputPassword;
    private MaterialButton     btnLogInEmail;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_login);

        inputEmail    = findViewById(R.id.inputEmail);
        inputPassword = findViewById(R.id.inputPassword);
        btnLogInEmail      = findViewById(R.id.btnLogInEmail);

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
                            if (!res.isSuccessful() || res.body() == null) {
                                Toast.makeText(EmailLoginActivity.this,
                                        "Login failed: " + res.code(),
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            // everything successful → hand off to our helper
                            onAuthSuccess(res.body().getToken(),
                                    res.body().getExpiresInMillis());
                        }
                        @Override
                        public void onFailure(Call<AuthResponse> call,
                                              Throwable t) {
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

    private void onAuthSuccess(String jwt, long expiresIn) {
        // Persist token & expiry
        long expiry = System.currentTimeMillis() + expiresIn;
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit()
                .putString("appToken", jwt)
                .putLong("appTokenExpiry", expiry)
                .apply();

        // Tell Retrofit to use it
        RetrofitClient.getInstance().setAuthToken(jwt);

        // Feedback to user
        Toast.makeText(this,
                "Login successful!",
                Toast.LENGTH_SHORT).show();

        // Navigate to MainActivity & finish this one
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
