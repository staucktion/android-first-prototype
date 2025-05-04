package com.example.staucktion;

import android.Manifest;
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
    private static final int   REQ_POST_NOTIFS = 1001;

    private TextInputEditText inputEmail, inputPassword;
    private MaterialButton    btnLogInEmail;
    private MaterialTextView  tvLoginError;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_login);

        inputEmail    = findViewById(R.id.inputEmail);
        inputPassword = findViewById(R.id.inputPassword);
        btnLogInEmail = findViewById(R.id.btnLogInEmail);
        tvLoginError  = findViewById(R.id.tvLoginError);

        MaterialTextView tvPrompt = findViewById(R.id.tvPrompt);
        tvPrompt.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class))
        );

        btnLogInEmail.setOnClickListener(v -> attemptEmailLogin());
    }

    private void attemptEmailLogin() {
        String email = inputEmail.getText().toString().trim();
        String pass  = inputPassword.getText().toString();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            inputEmail.setError("Invalid email");
            return;
        }
        if (pass.length() < 6) {
            inputPassword.setError("Password too short");
            return;
        }

        ApiService svc = RetrofitClient.getInstance().create(ApiService.class);
        svc.loginWithEmail(new EmailAuthRequest(email, pass))
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(Call<AuthResponse> call,
                                           Response<AuthResponse> res) {
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
                        onAuthSuccess(res.body().getToken());
                    }

                    @Override
                    public void onFailure(Call<AuthResponse> call, Throwable t) {
                        Log.e(TAG, "network error", t);
                        Toast.makeText(EmailLoginActivity.this,
                                "Network error: " + t.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void onAuthSuccess(String jwt) {
        // store token + expiry
        long expiry = extractExpiry(jwt);
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit()
                .putString("appToken", jwt)
                .putLong("appTokenExpiry", expiry)
                .apply();

        // configure Retrofit
        RetrofitClient.getInstance().setAuthToken(jwt);

        // fetch user info
        ApiService svc = RetrofitClient.getInstance().create(ApiService.class);
        svc.getUserInfo().enqueue(new Callback<UserInfoResponse>() {
            @Override
            public void onResponse(Call<UserInfoResponse> call,
                                   Response<UserInfoResponse> res) {
                if (res.isSuccessful() && res.body() != null) {
                    var user = res.body().getUser();
                    int    id    = user.getUserId();
                    String full  = user.getFirstName() + " " + user.getLastName();
                    String photo = user.getPhotoUrl() != null ? user.getPhotoUrl() : "";
                    String role  = user.getRoleName();

                    prefs.edit()
                            .putString("userFullName", full)
                            .putString("userPhotoUrl", photo)
                            .putString("userRole", role)
                            .apply();

                    OneSignal.setExternalUserId(
                            String.valueOf(id)
                    );
                }

                // whether we succeeded or not, move into the app
                enterMainWithNotificationPrompt();
            }

            @Override
            public void onFailure(Call<UserInfoResponse> call, Throwable t) {
                Timber.e(t, "profile fetch failed");
                enterMainWithNotificationPrompt();
            }
        });
    }

    private void enterMainWithNotificationPrompt() {
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // on Android 13+, ask for POST_NOTIFICATIONS if not yet granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{ Manifest.permission.POST_NOTIFICATIONS },
                    REQ_POST_NOTIFS
            );
        } else {
            // on older versions or already granted: go ahead
            startActivity(i);
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, perms, results);
        if (requestCode == REQ_POST_NOTIFS) {
            // either granted or denied, but we still proceed
            Intent i = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        }
    }

    private long extractExpiry(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            byte[]  decoded = Base64.decode(parts[1],
                    Base64.URL_SAFE|Base64.NO_PADDING|Base64.NO_WRAP);
            JSONObject obj = new JSONObject(new String(decoded, "UTF-8"));
            return obj.has("exp") ? obj.getLong("exp")*1000L
                    : System.currentTimeMillis() + 86_400_000L;
        } catch (Exception e) {
            Log.e(TAG, "jwt parse failed", e);
            return System.currentTimeMillis() + 86_400_000L;
        }
    }
}