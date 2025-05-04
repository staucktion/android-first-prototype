package com.example.staucktion;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.staucktion.api.ApiService;
import com.example.staucktion.api.RetrofitClient;
import com.example.staucktion.models.AuthResponse;
import com.example.staucktion.models.EmailAuthRequest;
import com.example.staucktion.models.UserInfoResponse;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.onesignal.OneSignal;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

public class EmailLoginActivity extends AppCompatActivity {
    private static final String TAG = "EmailLogin";
    private TextInputEditText inputEmail, inputPassword;
    private MaterialButton     btnLogInEmail;
    private MaterialTextView tvLoginError;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_login);

        inputEmail    = findViewById(R.id.inputEmail);
        inputPassword = findViewById(R.id.inputPassword);
        btnLogInEmail = findViewById(R.id.btnLogInEmail);
        tvLoginError = findViewById(R.id.tvLoginError);

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
                                String msg = "Login failed: " + res.code();
                                try {
                                    if (res.errorBody() != null) {
                                        String json = res.errorBody().string();
                                        JSONObject o = new JSONObject(json);
                                        msg = o.optString("message", msg);
                                    }
                                } catch (IOException | JSONException e) {
                                    Log.e(TAG, "error parsing login error", e);
                                }
                                Toast.makeText(EmailLoginActivity.this, msg, Toast.LENGTH_LONG).show();
                                return;
                            }
                            tvLoginError.setVisibility(View.GONE);
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
        MaterialTextView tvPrompt = findViewById(R.id.tvPrompt);
        tvPrompt.setOnClickListener(v ->
                startActivity(new Intent(EmailLoginActivity.this, RegisterActivity.class))
        );
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
                if (res.isSuccessful() && res.body()!=null) {
                    int userId = res.body().getUser().getUserId();
                    String first   = res.body().getUser().getFirstName();
                    String last    = res.body().getUser().getLastName();
                    String fullName= first + " " + last;
                    String photoUrl= res.body().getUser().getPhotoUrl();
                    String roleName = res.body().getUser().getRoleName();  // “admin” or “validator”

                    // 2) Save to prefs
                    prefs.edit()
                            .putString("userFullName", fullName)
                            .putString("userPhotoUrl", photoUrl != null ? photoUrl : "")
                            .putString("userRole", roleName)
                            .apply();
                    Timber.d("EmailLoginActivity → stored userRole = %s", roleName);

                    // 3) Tell OneSignal who we are
                    OneSignal.setExternalUserId(
                            String.valueOf(userId),
                            new OneSignal.OSExternalUserIdUpdateCompletionHandler() {
                                @Override
                                public void onSuccess(JSONObject results) {
                                    Timber.d("OneSignal external ID set (email): %s", results);
                                }
                                @Override
                                public void onFailure(@NonNull OneSignal.ExternalIdError error) {
                                    Timber.e("OneSignal external ID error (email): %s", error);
                                }
                            }
                    );
                }

                // 4) Finally launch MainActivity
                navigateToMainWithNotifications();
            }

            @Override
            public void onFailure(@NonNull Call<UserInfoResponse> call, @NonNull Throwable t) {
                // Log the network or parsing error
                Timber.e(t, "Network error fetching user info (email login)");

                // Optionally show a toast so the user knows something went wrong fetching their profile
                Toast.makeText(EmailLoginActivity.this,
                        "Warning: couldn’t load profile — proceeding anyway",
                        Toast.LENGTH_SHORT).show();

                // Finally, navigate to MainActivity regardless
                navigateToMainWithNotifications();
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
    private void navigateToMainWithNotifications() {
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this,
                        android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {

            // ask the user for notifications
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{ android.Manifest.permission.POST_NOTIFICATIONS },
                    1001
            );
            // return here; onRequestPermissionsResult will finish the login
            return;
        }

        // either we already have permission or we're on < Android 13
        startActivity(i);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == 1001) {
            // optional: toast if the user denied
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

}