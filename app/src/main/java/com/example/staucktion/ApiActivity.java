package com.example.staucktion;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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

    // The category name if we're creating a new location/category
    private String newLocationName = "";
    private String imagePath;

    // We'll capture device info for the upload request
    private String deviceInfo = "Unknown Device";

    private LocationApiManager locationApiManager;
    private CategoryManager categoryManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Use a blank view since this activity runs background tasks
        setContentView(new View(this));

        locationApiManager = new LocationApiManager();

        // Check CAMERA permission; if not granted, request it
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CODE_PERMISSION
            );
        } else {
            initActivity();
        }
    }

    @SuppressLint("TimberArgCount")
    private void initActivity() {
        // Retrieve token and expiry from SharedPreferences.
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String appToken = prefs.getString("appToken", "");
        long tokenExpiry = prefs.getLong("appTokenExpiry", 0);

        // Check if token exists and is still valid.
        if (appToken.isEmpty() || tokenExpiry == 0 || System.currentTimeMillis() > tokenExpiry) {
            prefs.edit().remove("appToken").remove("appTokenExpiry").apply();
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        } else {
            RetrofitClient.getInstance().setAuthToken(appToken);
        }

        // Initialize ApiService and CategoryManager
        apiService = RetrofitClient.getInstance().create(ApiService.class);
        categoryManager = new CategoryManager();

        // Capture some basic device info
        deviceInfo = getDeviceInfo();

        // Retrieve intent extras
        imagePath = getIntent().getStringExtra("image_path");
        boolean isRequestNewLocation = getIntent().getBooleanExtra("isRequestNewLocation", false);

        if (isRequestNewLocation) {
            // For a new location request, newLocationName is the category name
            newLocationName = getIntent().getStringExtra("newLocationName");
            if (newLocationName == null || newLocationName.isEmpty()) {
                Toast.makeText(this, "New location (category) name is missing", Toast.LENGTH_SHORT).show();
                returnToMain();
                return;
            }
            // Create a new location, then category, then upload photo
            createLocationThenCategoryAndUpload();
        } else {
            // Use existing category ID from intent
            categoryId = getIntent().getIntExtra("category_id", -1);
            if (categoryId == -1 || imagePath == null || imagePath.isEmpty()) {
                Toast.makeText(this, "Required data missing.", Toast.LENGTH_SHORT).show();
                returnToMain();
                return;
            }
            // Directly upload photo
            uploadPhoto(new File(imagePath), categoryId);
        }
    }

    /**
     * Creates a new location with dummy coordinates, then a category, then uploads the photo.
     */
    private void createLocationThenCategoryAndUpload() {
        double latitude = 40.1234;   // Example lat
        double longitude = 29.5678;  // Example lon

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

    /**
     * Creates a new category for the given location, then uploads the photo.
     */
    private void createCategory(int locationId) {
        // Build a CategoryRequest with the new location name
        CategoryRequest categoryRequest = new CategoryRequest(
                newLocationName,
                "Unknown Address",
                5.0,
                locationId,
                1
        );

        apiService.createCategory(categoryRequest).enqueue(new Callback<CategoryResponse>() {
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

    /**
     * Uploads the photo with the given category ID and the required deviceInfo field.
     */
    private void uploadPhoto(File photoFile, int categoryId) {
        if (!photoFile.exists()) {
            Toast.makeText(this, "Photo file does not exist.", Toast.LENGTH_SHORT).show();
            returnToMain();
            return;
        }

        // Build multipart parts
        RequestBody requestFile = RequestBody.create(MediaType.get("image/jpeg"), photoFile);
        MultipartBody.Part photoPart = MultipartBody.Part.createFormData("photo", photoFile.getName(), requestFile);

        RequestBody categoryIdBody = RequestBody.create(MediaType.get("text/plain"), String.valueOf(categoryId));
        RequestBody deviceInfoBody = RequestBody.create(MediaType.get("text/plain"), deviceInfo);

        // Now call the uploadPhoto endpoint that expects "photo", "categoryId", and "deviceInfo"
        apiService.uploadPhoto(photoPart, categoryIdBody, deviceInfoBody).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call,
                                   @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(ApiActivity.this, "Upload successful!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ApiActivity.this, "Upload failed: " + response.message(), Toast.LENGTH_SHORT).show();
                }
                returnToMain();
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Toast.makeText(ApiActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                returnToMain();
            }
        });
    }

    /**
     * Returns a simple device info string. You can enhance this to gather real device details.
     */
    private String getDeviceInfo() {
        // Example: manufacturer, model, OS version
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        String version = Build.VERSION.RELEASE;
        return manufacturer + " " + model + " (Android " + version + ")";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSION && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initActivity();
        } else {
            Toast.makeText(this, "Camera permission required.", Toast.LENGTH_SHORT).show();
            returnToMain();
        }
    }

    private void returnToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
