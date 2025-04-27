package com.example.staucktion;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.staucktion.api.ApiService;
import com.example.staucktion.api.RetrofitClient;
import com.example.staucktion.models.AuthResponse;
import com.example.staucktion.models.RegisterRequest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {
    private TextInputEditText firstName;
    private TextInputEditText lastName;
    private TextInputEditText email;
    private TextInputEditText password;
    private TextInputEditText confirmPassword;
    private MaterialButton    btnSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        firstName       = findViewById(R.id.inputFirstName);
        lastName        = findViewById(R.id.inputLastName);
        email           = findViewById(R.id.inputEmail);
        password        = findViewById(R.id.inputPassword);
        confirmPassword = findViewById(R.id.confirmPassword);
        btnSubmit       = findViewById(R.id.btnRegister);

        btnSubmit.setOnClickListener(v -> attemptRegister());

        // “Already have an account? Log in here!”
        MaterialTextView tvPrompt = findViewById(R.id.tvPrompt);
        tvPrompt.setOnClickListener(v ->
                startActivity(new Intent(RegisterActivity.this, EmailLoginActivity.class))
        );
    }

    private void attemptRegister() {
        // 1) Clear previous errors
        firstName.setError(null);
        lastName.setError(null);
        email.setError(null);
        password.setError(null);
        confirmPassword.setError(null);

        // 2) Read input
        String fn = firstName.getText().toString().trim();
        String ln = lastName.getText().toString().trim();
        String em = email.getText().toString().trim();
        String pw = password.getText().toString();
        String cw = confirmPassword.getText().toString();

        boolean cancel = false;

        // 3) Validate non-empty
        if (fn.isEmpty()) {
            firstName.setError("Required");
            cancel = true;
        }
        if (ln.isEmpty()) {
            lastName.setError("Required");
            cancel = true;
        }
        if (em.isEmpty()) {
            email.setError("Required");
            cancel = true;
        }
        if (pw.isEmpty()) {
            password.setError("Required");
            cancel = true;
        }
        if (cw.isEmpty()) {
            confirmPassword.setError("Required");
            cancel = true;
        }

        // 4) Password length
        if (!pw.isEmpty() && pw.length() < 8) {
            password.setError("Password must be at least 8 characters long");
            cancel = true;
        }

        // 5) Passwords match
        if (!pw.isEmpty() && !cw.isEmpty() && !pw.equals(cw)) {
            confirmPassword.setError("Passwords do not match");
            cancel = true;
        }

        if (cancel) {
            // there were validation errors
            return;
        }

        // 6) All good → call register API
        RegisterRequest req = new RegisterRequest(em, pw, fn, ln);
        RetrofitClient
                .getInstance()
                .create(ApiService.class)
                .registerWithEmail(req)
                .enqueue(new Callback<AuthResponse>() {
                    @Override public void onResponse(
                            Call<AuthResponse> call,
                            Response<AuthResponse> res
                    ) {
                        if (!res.isSuccessful() || res.body() == null) {
                            Toast.makeText(RegisterActivity.this,
                                    "Registration failed: HTTP " + res.code(),
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // Persist token exactly as in login:
                        String jwt = res.body().getToken();
                        long ttl   = res.body().getExpiresInMillis() > 0
                                ? res.body().getExpiresInMillis()
                                : 86_400_000L;
                        long exp   = System.currentTimeMillis() + ttl;

                        SharedPreferences prefs =
                                getSharedPreferences("AppPrefs", MODE_PRIVATE);
                        prefs.edit()
                                .putString("appToken", jwt)
                                .putLong("appTokenExpiry", exp)
                                .putString("userName", fn + " " + ln)
                                .apply();

                        RetrofitClient.getInstance().setAuthToken(jwt);

                        // Done — return to login (or go straight into main):
                        setResult(RESULT_OK);
                        finish();
                    }
                    @Override public void onFailure(Call<AuthResponse> call, Throwable t) {
                        Toast.makeText(RegisterActivity.this,
                                "Network error: " + t.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
