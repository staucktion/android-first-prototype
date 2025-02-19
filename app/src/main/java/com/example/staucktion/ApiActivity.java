package com.example.staucktion;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AppCompatActivity;

import com.example.staucktion.api.ApiService;
import com.example.staucktion.api.RetrofitClient;
import com.google.android.material.button.MaterialButton;

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

    private static final int REQUEST_CODE_PERMISSION = 1;
    private static final int REQUEST_CAMERA_CAPTURE = 100;

    // Views from the main layout
    private ImageView logo;
    private TextView title;
    private MaterialButton mbtn;

    // Networking
    private RetrofitClient retrofitClient;
    private ApiService apiService;

    // Reference to MainActivity singleton
    private MainActivity mainActivity;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        // Use the main layout instead of activity_api
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Find views from the main layout
        logo = findViewById(R.id.logo);
        title = findViewById(R.id.title);
        mbtn = findViewById(R.id.mbtn);

        if (mbtn == null) {
            Toast.makeText(this, "Button not found. Check your layout file.", Toast.LENGTH_SHORT).show();
        }

        // Request CAMERA permission if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_PERMISSION);
        }

        // Initialize Retrofit and ApiService
        retrofitClient = new RetrofitClient();
        apiService = retrofitClient.getInstance().create(ApiService.class);

        // Get the MainActivity singleton (if it exists)
        mainActivity = MainActivity.getInstance();

        // Set click listener for the camera button
        mbtn.setOnClickListener(v -> {
            if (mainActivity != null && mainActivity.getIsGpsEnabled()) {
                Intent cameraIntent = new Intent(this, CameraActivity.class);
                if (cameraIntent.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(cameraIntent, REQUEST_CAMERA_CAPTURE);
                    mainActivity.setIsCameraActive(true);
                    mainActivity.setHasGPSTurnedOffOnceWhileInCamera(false);
                } else {
                    Toast.makeText(this, "No Activity found to handle camera action", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this,
                        "Please turn on location services to be able to take a photo.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (mainActivity != null) {
            if (mainActivity.isCameraActivityFinishedProperly(requestCode, resultCode, data)) {
                String imagePath = data.getStringExtra("image_path");
                File photoFile = new File(imagePath);
                uploadPhoto(photoFile);
            } else {
                Toast.makeText(this,
                        "Camera activity did not finish properly or GPS was off.",
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this,
                    "Something went wrong while handling the camera activity result",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadPhoto(File photoFile) {
        if (photoFile == null) {
            return;
        }

        try {
            RequestBody requestFile = RequestBody.create(MediaType.get("image/*"), photoFile);
            MultipartBody.Part body = MultipartBody.Part.createFormData("photo", photoFile.getName(), requestFile);

            Call<ResponseBody> call = apiService.uploadPhoto(body);
            call.enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful()) {
                        if (response.body() != null) {
                            String responseBody;
                            try {
                                responseBody = response.body().string();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            Toast.makeText(ApiActivity.this, "Upload successful!", Toast.LENGTH_SHORT).show();
                        } else if (response.code() == 204) {
                            Toast.makeText(ApiActivity.this, "Upload successful!", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(ApiActivity.this, "Upload failed: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Toast.makeText(ApiActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "File error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
