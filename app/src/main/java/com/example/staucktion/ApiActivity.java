package com.example.staucktion;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.staucktion.api.ApiService;
import com.example.staucktion.api.RetrofitClient;

import java.io.File;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

//class StatusEnum { public static final int WAIT = 2; }

public class ApiActivity extends AppCompatActivity {

    private ApiService apiService;
    private int categoryId = -1;
    private String newLocationName = "";
    private String imagePath;
    private String deviceInfo;

    private TextView uploadingText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api);

        uploadingText = findViewById(R.id.uploadingText);
        uploadingText.setVisibility(View.VISIBLE); // Initially visible

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String appToken = prefs.getString("appToken", "");
        long tokenExpiry = prefs.getLong("appTokenExpiry", 0);

        if (appToken.isEmpty() || tokenExpiry == 0 || System.currentTimeMillis() > tokenExpiry) {
            prefs.edit().clear().apply();
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        RetrofitClient.getInstance().setAuthToken(appToken);
        apiService = RetrofitClient.getInstance().create(ApiService.class);
        deviceInfo = getDeviceInfo();

        imagePath = getIntent().getStringExtra("image_path");
        categoryId = getIntent().getIntExtra("theme_id", -1); //category_id should stay

        if (categoryId == -1 || imagePath == null || imagePath.isEmpty()) {
            Toast.makeText(this, "Required data missing.", Toast.LENGTH_SHORT).show();
            returnToMain();
            return;
        }

        uploadPhoto(new File(imagePath), categoryId);
    }

    private void uploadPhoto(File photoFile, int categoryId) {
        RequestBody fileBody = RequestBody.create(MediaType.get("image/jpeg"), photoFile);
        MultipartBody.Part photoPart = MultipartBody.Part.createFormData("photo", photoFile.getName(), fileBody);
        RequestBody categoryIdBody = RequestBody.create(MediaType.get("text/plain"), String.valueOf(categoryId));
        RequestBody deviceInfoBody = RequestBody.create(MediaType.get("text/plain"), deviceInfo);

        uploadingText.setVisibility(View.VISIBLE); // Show uploading text

        apiService.uploadPhoto(photoPart, categoryIdBody, deviceInfoBody).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                uploadingText.setVisibility(View.GONE); // Hide uploading text
                Toast.makeText(ApiActivity.this, response.isSuccessful() ? "Upload successful!" : "Upload failed.", Toast.LENGTH_SHORT).show();
                returnToMain();
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                uploadingText.setVisibility(View.GONE); // Hide uploading text
                Toast.makeText(ApiActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                returnToMain();
            }
        });
    }

    private String getDeviceInfo() {
        return Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")";
    }

    private void returnToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
