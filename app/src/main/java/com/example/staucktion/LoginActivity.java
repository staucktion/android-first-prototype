package com.example.staucktion;

import static com.example.staucktion.R.layout.activity_login;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.staucktion.api.ApiService;
import com.example.staucktion.api.RetrofitClient;
import com.example.staucktion.models.AuthRequest;
import com.example.staucktion.models.AuthResponse;
import com.example.staucktion.models.UserInfoResponse;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.gson.Gson;
import com.onesignal.OneSignal;
import com.onesignal.OneSignal.ExternalIdError;
import com.onesignal.OneSignal.OSExternalUserIdUpdateCompletionHandler;

import org.json.JSONObject;

import java.io.IOException;

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
        setContentView(activity_login);

        // 1) If we already have a valid JWT, skip ahead:
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String savedJwt    = prefs.getString("appToken", null);
        long   savedExpiry = prefs.getLong("appTokenExpiry", 0);
        // instead of checking Google alone, combine it:
        // only skip when your token is still valid
        if (savedJwt != null
                && System.currentTimeMillis() < savedExpiry) {
            goToMainActivity();
            return;
        }

        // 2) Configure Google Sign‑In:
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);


        // 4) Otherwise show the “Sign in with Google” button:
        Button btnLogin = findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(v ->
                startActivityForResult(
                        googleSignInClient.getSignInIntent(),
                        RC_SIGN_IN
                )
        );
    }

    @SuppressLint("LogNotTimber")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != RC_SIGN_IN) return;

        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount acct = task.getResult(ApiException.class);
            if (acct != null && acct.getIdToken() != null) {
                exchangeGoogleTokenForJwt(acct);
            } else {
                Toast.makeText(this, "Google sign‑in failed", Toast.LENGTH_SHORT).show();
            }
        } catch (ApiException e) {
            Toast.makeText(this,
                    "Google sign‑in exception: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }
    private void exchangeGoogleTokenForJwt(GoogleSignInAccount acct) {
        // 1) Prepare your ApiService
        ApiService svc = RetrofitClient.getInstance().create(ApiService.class);

        // 2) Exchange Google token for your JWT
        svc.loginWithGoogle(new AuthRequest(acct.getIdToken()))
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthResponse> call,
                                           @NonNull Response<AuthResponse> res) {
                        if (!res.isSuccessful() || res.body() == null) {
                            // handle error
                            String errJson = "";
                            try {
                                if (res.errorBody() != null) errJson = res.errorBody().string();
                            } catch (IOException e) {
                                Timber.e(e, "Failed to read errorBody()");
                            }
                            Timber.e("Login failed, errorBody=%s", errJson);
                            Toast.makeText(LoginActivity.this, "Login failed", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // 3) Got a valid JWT
                        AuthResponse auth = res.body();
                        String jwt = auth.getToken();
                        long ttl = auth.getExpiresInMillis() > 0
                                ? auth.getExpiresInMillis()
                                : 86_400_000L;
                        long expiry = System.currentTimeMillis() + ttl;

                        // 4) Tell Retrofit to use this token
                        RetrofitClient.getInstance().setAuthToken(jwt);

                        // 5) Now fetch the full user info (to get your numeric ID)
                        svc.getUserInfo().enqueue(new Callback<UserInfoResponse>() {
                            @SuppressLint("TimberArgTypes")
                            @Override
                            public void onResponse(@NonNull Call<UserInfoResponse> call,
                                                   @NonNull Response<UserInfoResponse> userRes) {

                                // fallback to Google ID if /auth/info fails
                                // 1) Start with a fallback (Google’s string ID parsed to long)
                                UserInfoResponse wrapper = userRes.body();
                                int userId = wrapper.getUser().getUserId();

                                // 3) Persist to SharedPreferences (convert back to String if you store as String)
                                SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                                prefs.edit()
                                        .putString("appToken", jwt)
                                        .putLong("appTokenExpiry", expiry)
                                        .putString("userId", String.valueOf(userId))
                                        .putString("userName", acct.getDisplayName())
                                        .putString("userPhotoUrl",
                                                acct.getPhotoUrl() != null
                                                        ? acct.getPhotoUrl().toString()
                                                        : ""
                                        )
                                        .apply();
                                Timber.d("User Id is: %d", userId);
                                // 7) Wire up OneSignal external ID
                                OneSignal.setExternalUserId(  String.valueOf(userId),
                                        new OSExternalUserIdUpdateCompletionHandler() {
                                            @Override
                                            public void onSuccess(JSONObject results) {
                                                Timber.d("OneSignal external ID set: %s", results);
                                            }
                                            @Override
                                            public void onFailure(@NonNull ExternalIdError error) {
                                                Timber.e("OneSignal external ID error: %s", error);
                                            }
                                        }
                                );

                                // 8) Finally, go to main screen
                                goToMainActivity();
                            }

                            @Override
                            public void onFailure(@NonNull Call<UserInfoResponse> call,
                                                  @NonNull Throwable t) {
                                Timber.e(t, "Network error fetching user info");
                                // still let them in
                                goToMainActivity();
                            }
                        });
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthResponse> call,
                                          @NonNull Throwable t) {
                        Toast.makeText(LoginActivity.this,
                                "Network error during login", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void goToMainActivity() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
