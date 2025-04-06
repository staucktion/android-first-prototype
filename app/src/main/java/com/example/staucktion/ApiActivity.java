package com.example.staucktion;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.staucktion.api.ApiService;
import com.example.staucktion.api.CategoryManager;
import com.example.staucktion.api.RetrofitClient;
import com.example.staucktion.managers.LocationApiManager;
import com.example.staucktion.models.CategoryRequest;
import com.example.staucktion.models.CategoryResponse;
import com.example.staucktion.models.LocationCreateResponse;

import java.io.File;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ApiActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PERMISSION = 1;

    private ApiService apiService;
    private int categoryId = -1;
    private String newLocationName = "";
    private String imagePath;
    private String deviceInfo;

    private LocationApiManager locationApiManager;
    private CategoryManager categoryManager;
    private LocationManager locationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new View(this));

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationApiManager = new LocationApiManager();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_PERMISSION);
        } else {
            initActivity();
        }
    }

    private void initActivity() {
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
        categoryManager = new CategoryManager();

        deviceInfo = getDeviceInfo();

        imagePath = getIntent().getStringExtra("image_path");
        boolean isRequestNewLocation = getIntent().getBooleanExtra("isRequestNewLocation", false);

        if (isRequestNewLocation) {
            newLocationName = getIntent().getStringExtra("newLocationName");
            if (newLocationName == null || newLocationName.isEmpty()) {
                Toast.makeText(this, "New location name is missing", Toast.LENGTH_SHORT).show();
                returnToMain();
                return;
            }
            createLocationThenCategoryAndUpload();
        } else {
            categoryId = getIntent().getIntExtra("category_id", -1);
            if (categoryId == -1 || imagePath == null || imagePath.isEmpty()) {
                Toast.makeText(this, "Required data missing.", Toast.LENGTH_SHORT).show();
                returnToMain();
                return;
            }
            uploadPhoto(new File(imagePath), categoryId);
        }
    }

    private void createLocationThenCategoryAndUpload() {
        double latitude = 40.1234;
        double longitude = 29.5678;

        locationApiManager.createLocation(latitude, longitude, new Callback<LocationCreateResponse>() {
            @Override
            public void onResponse(@NonNull Call<LocationCreateResponse> call,
                                   @NonNull Response<LocationCreateResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getLocation() != null) {
                    int locationId = Integer.parseInt(response.body().getLocation().getId());
                    createCategory(locationId);
                } else {
                    Toast.makeText(ApiActivity.this, "Location creation failed.", Toast.LENGTH_SHORT).show();
                    returnToMain();
                }
            }

            @Override
            public void onFailure(@NonNull Call<LocationCreateResponse> call, @NonNull Throwable t) {
                Toast.makeText(ApiActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                returnToMain();
            }
        });
    }

    private void createCategory(int locationId) {
        CategoryRequest request = new CategoryRequest(
                newLocationName,
                "Unknown Address",
                5.0,
                locationId,
                1
        );

        apiService.createCategory(request).enqueue(new Callback<CategoryResponse>() {
            @Override
            public void onResponse(@NonNull Call<CategoryResponse> call,
                                   @NonNull Response<CategoryResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    categoryId = Integer.parseInt(response.body().getId());
                    uploadPhoto(new File(imagePath), categoryId);
                } else {
                    Toast.makeText(ApiActivity.this, "Category creation failed.", Toast.LENGTH_SHORT).show();
                    returnToMain();
                }
            }

            @Override
            public void onFailure(@NonNull Call<CategoryResponse> call, @NonNull Throwable t) {
                Toast.makeText(ApiActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                returnToMain();
            }
        });
    }

    private void uploadPhoto(File photoFile, int categoryId) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean gpsStatusAtStart = prefs.getBoolean("gpsStatusOnCameraStart", true);
        boolean gpsDisabledDuringCamera = prefs.getBoolean("gpsDisabledDuringCamera", false);
        boolean isGpsOnNow = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!gpsStatusAtStart || gpsDisabledDuringCamera || !isGpsOnNow) {
            Toast.makeText(this,
                    "GPS was disabled during photo capture. Upload failed. Please enable GPS and try again.",
                    Toast.LENGTH_LONG).show();
            returnToMain();
            return;
        }

        // Proceed with upload if all checks pass.
        RequestBody fileBody = RequestBody.create(MediaType.get("image/jpeg"), photoFile);
        MultipartBody.Part photoPart = MultipartBody.Part.createFormData("photo", photoFile.getName(), fileBody);
        RequestBody categoryIdBody = RequestBody.create(MediaType.get("text/plain"), String.valueOf(categoryId));
        RequestBody deviceInfoBody = RequestBody.create(MediaType.get("text/plain"), deviceInfo);

        apiService.uploadPhoto(photoPart, categoryIdBody, deviceInfoBody).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                Toast.makeText(ApiActivity.this, response.isSuccessful() ? "Upload successful!" : "Upload failed.", Toast.LENGTH_SHORT).show();
                returnToMain();
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
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
