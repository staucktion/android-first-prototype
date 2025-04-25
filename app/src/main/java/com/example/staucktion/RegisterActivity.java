package com.example.staucktion;

import android.annotation.SuppressLint;
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
    private TextInputEditText firstName, lastName, email, password;
    private MaterialButton btnSubmit;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        firstName  = findViewById(R.id.inputFirstName);
        lastName   = findViewById(R.id.inputLastName);
        email      = findViewById(R.id.inputEmail);
        password   = findViewById(R.id.inputPassword);
        btnSubmit  = findViewById(R.id.btnRegister);

        btnSubmit.setOnClickListener(v -> {
            String fn = firstName.getText().toString().trim();
            String ln = lastName.getText().toString().trim();
            String em = email.getText().toString().trim();
            String pw = password.getText().toString().trim();

            if (fn.isEmpty() || ln.isEmpty() || em.isEmpty() || pw.isEmpty()) {
                Toast.makeText(this, "All fields are required.", Toast.LENGTH_SHORT).show();
                return;
            }

            RegisterRequest req = new RegisterRequest(em, pw, fn, ln);
            RetrofitClient.getInstance()
                    .create(ApiService.class)
                    .registerWithEmail(req)
                    .enqueue(new Callback<AuthResponse>() {
                        @Override public void onResponse(Call<AuthResponse> c, Response<AuthResponse> r) {
                            if (!r.isSuccessful() || r.body()==null) {
                                Toast.makeText(RegisterActivity.this,
                                        "Register failed: HTTP "+r.code(), Toast.LENGTH_SHORT).show();
                                return;
                            }
                            // same token/storage flow as in LoginActivity:
                            String jwt = r.body().getToken();
                            long   ttl = r.body().getExpiresInMillis();
                            long   exp = System.currentTimeMillis() + (ttl>0?ttl:86_400_000);

                            SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                            prefs.edit()
                                    .putString("appToken", jwt)
                                    .putLong("appTokenExpiry", exp)
                                    .putString("userName", fn + " " + ln)
                                    .apply();

                            RetrofitClient.getInstance().setAuthToken(jwt);
                            setResult(RESULT_OK);
                            finish();
                        }
                        @Override public void onFailure(Call<AuthResponse> c, Throwable t) {
                            Toast.makeText(RegisterActivity.this,
                                    "Network error: "+t.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        });
        @SuppressLint({"MissingInflatedId", "LocalSuppress"}) MaterialTextView tvPrompt = findViewById(R.id.tvPrompt);
        tvPrompt.setOnClickListener(v -> {
            // if you have a dedicated RegisterActivity:
            startActivity(new Intent(RegisterActivity.this, EmailLoginActivity.class));
        });
    }
}
