package com.example.staucktion;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.staucktion.api.ApiService;
import com.example.staucktion.api.RetrofitClient;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ApiActivity extends AppCompatActivity {

    Button btnHealthCheck;
    Button btnGoBack;
    TextView tvApi;
    TextView tvCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_api);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        btnHealthCheck = findViewById(R.id.btnHealthCheck);
        tvApi = findViewById(R.id.tvApi);
        tvCode = findViewById(R.id.tvCode);
        btnGoBack = findViewById(R.id.btnGoBack);

        // health check button click
        btnHealthCheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("HealthCheck", "HealthCheck button clicked");
                tvCode.setText("loading");
                tvApi.setText("loading");

                // Create the API Service
                ApiService apiService = RetrofitClient.getInstance().create(ApiService.class);

                // Call the GET endpoint
                Call<ResponseBody> call = apiService.getHealthStatus();

                // Perform the network request
                call.enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                String responseBody = response.body().string();
                                String responseCode = response.code() + "";
                                tvApi.setText(responseBody);
                                tvCode.setText(responseCode);
                                Log.d("HealthCheck", "Response: " + responseBody);
                                Log.d("HealthCheck", "Code: " + responseCode);
                            } catch (Exception e) {
                                tvApi.setText(e.getMessage());
                                Log.e("HealthCheck", "Error reading response: " + e.getMessage());
                            }
                        } else {
                            Log.e("HealthCheck", "Request failed. Code: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Log.e("HealthCheck", "Network request failed: " + t.getMessage());
                    }
                });
            }
        });


        // return button click
        btnGoBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}