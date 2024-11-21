package com.example.staucktion;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.staucktion.api.ApiService;
import com.example.staucktion.api.RetrofitClient;
import com.example.staucktion.utils.FileUtils;

import java.io.File;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class ApiActivity extends AppCompatActivity {

    Button btnHealthCheck;
    Button btnGoBack;
    Button btnUploadPhoto;
    TextView tvApi;
    TextView tvCode;
    ApiService apiService;
    private static final int REQUEST_CODE_PERMISSION = 1;

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

        // connect components
        btnHealthCheck = findViewById(R.id.btnHealthCheck);
        btnUploadPhoto = findViewById(R.id.btnUploadPhoto);
        tvApi = findViewById(R.id.tvApi);
        tvCode = findViewById(R.id.tvCode);
        btnGoBack = findViewById(R.id.btnGoBack);

        // Request permissions if not already granted
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE_PERMISSION);
        }

        // Create the API Service
        apiService = RetrofitClient.getInstance().create(ApiService.class);

        // health check button click
        btnHealthCheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("HealthCheck", "HealthCheck button clicked");
                tvCode.setText("loading");
                tvApi.setText("loading");

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

        // Activity Result API for selecting an image
        ActivityResultLauncher<Intent> photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedPhotoUri = result.getData().getData();
                        uploadPhoto(selectedPhotoUri);
                    } else {
                        Toast.makeText(this, "No photo selected", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // upload photo click
        btnUploadPhoto.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            photoPickerLauncher.launch(intent);
        });
    }

    private void uploadPhoto(Uri photoUri) {
        tvApi.setText("loading");
        tvCode.setText("loading");

        if (photoUri == null) return;

        try {
            // Convert the URI to a File
            File photoFile = new File(FileUtils.getPath(this, photoUri));

            // Create a RequestBody for the photo
            RequestBody requestFile = RequestBody.create(MediaType.get("image/*"), photoFile);

            // MultipartBody.Part is used to send the actual file
            MultipartBody.Part body = MultipartBody.Part.createFormData("photo", photoFile.getName(), requestFile);

            // Call the API
            Call<ResponseBody> call = apiService.uploadPhoto(body);
            call.enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful()) {
                        try {
                            String responseBody = response.body().string();
                            String responseCode = response.code() + "";
                            tvApi.setText(responseBody);
                            tvCode.setText(responseCode);
                            Toast.makeText(ApiActivity.this, "Upload successful!", Toast.LENGTH_SHORT).show();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        Toast.makeText(ApiActivity.this, "Upload failed: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Toast.makeText(ApiActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.d("upload", t.getMessage());
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "File error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.d("select", e.getMessage());

        }
    }
}