package com.example.staucktion;

import static com.example.staucktion.R.layout.activity_main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.staucktion.api.ApiService;
import com.example.staucktion.api.OnAddressObtainedListener;
import com.example.staucktion.api.RetrofitClient;
import com.example.staucktion.managers.LocationApiManager;
import com.example.staucktion.models.CategoryRequest;
import com.example.staucktion.models.CategoryResponse;
import com.example.staucktion.models.LocationCreateResponse;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 101;
    private static MainActivity instance;
    private LocationManager locationManager;
    private FusedLocationProviderClient fusedLocationClient;

    // Coordinates obtained dynamically (fallback to dummy if necessary)
    private double currentLatitude = 0;
    private double currentLongitude = 0;

    // Holds the category ID to use for the upload.
    private int selectedCategoryId = -1;

    private ApiService apiService;
    private LocationApiManager locationApiManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(activity_main);
        instance = this;

        Timber.plant(new Timber.DebugTree());
        Timber.i("Starting MainActivity");

        // Token check to ensure the user is logged in
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String appToken = prefs.getString("appToken", null);
        long tokenExpiry = prefs.getLong("appTokenExpiry", 0);
        if (appToken == null || tokenExpiry == 0 || System.currentTimeMillis() > tokenExpiry) {
            prefs.edit().remove("appToken").remove("appTokenExpiry").apply();
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        } else {
            RetrofitClient.getInstance().setAuthToken(appToken);
        }

        // Initialize managers and services
        apiService = RetrofitClient.getInstance().create(ApiService.class);
        locationApiManager = new LocationApiManager();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Check for location permission before obtaining location
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getLastLocation();
        }

        // Set up UI buttons
        Button openCameraBtn = findViewById(R.id.openCamerabtn);
        Button profileBtn = findViewById(R.id.profilebtn);

        openCameraBtn.setOnClickListener(v -> {
            // Use the obtained coordinates to check for an existing category.
            checkCategoryAtCoordinates(currentLatitude, currentLongitude);
        });

        profileBtn.setOnClickListener(v -> {
            Intent profileIntent = new Intent(MainActivity.this, ProfileActivity.class);
            startActivity(profileIntent);
        });
    }

    /**
     * Retrieves the last known location from the fused location provider.
     */
    @SuppressLint("MissingPermission")
    private void getLastLocation() {
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                currentLatitude = location.getLatitude();
                currentLongitude = location.getLongitude();
                Timber.i("Obtained location: lat=%f, lon=%f", currentLatitude, currentLongitude);
            } else {
                Timber.w("Location is null; using fallback dummy coordinates.");
                currentLatitude = 40.1234;
                currentLongitude = 29.5678;
            }
        }).addOnFailureListener(e -> {
            Timber.e(e, "Failed to obtain location; using fallback dummy coordinates.");
            currentLatitude = 40.1234;
            currentLongitude = 29.5678;
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, Objects.requireNonNull(permissions), grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLastLocation();
        } else {
            Toast.makeText(this, "Location permission required.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * Checks via your backend if an existing category exists for the given coordinates.
     */
    private void checkCategoryAtCoordinates(double latitude, double longitude) {
        apiService.getCategoryByCoordinates(latitude, longitude).enqueue(new Callback<CategoryResponse>() {
            @Override
            public void onResponse(@NonNull Call<CategoryResponse> call,
                                   @NonNull Response<CategoryResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Category exists – retrieve its ID.
                    selectedCategoryId = Integer.parseInt(response.body().getId());
                    Timber.i("Found existing category with ID: %d", selectedCategoryId);
                    launchCamera();
                } else {
                    // No category found; prompt for new category details.
                    Timber.i("No existing category found. Prompting for new location and category.");
                    promptForCategoryNameAndCreateCategory(latitude, longitude);
                }
            }

            @Override
            public void onFailure(@NonNull Call<CategoryResponse> call, @NonNull Throwable t) {
                Timber.e(t, "Error checking category by coordinates");
                Toast.makeText(MainActivity.this, "Network error while checking category", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Prompts the user for a category name, then creates a new location and category.
     */
    private void promptForCategoryNameAndCreateCategory(final double latitude, final double longitude) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Category Name");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("e.g., Cüneyt Hoca's Room");
        builder.setView(input);

        builder.setPositiveButton("OK", (DialogInterface dialog, int which) -> {
            String categoryName = input.getText().toString().trim();
            if (categoryName.isEmpty()) {
                Toast.makeText(MainActivity.this, "Category name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            createLocationAndCategory(latitude, longitude, categoryName);
        });
        builder.setNegativeButton("Cancel", (DialogInterface dialog, int which) -> dialog.cancel());
        builder.show();
    }

    /**
     * Creates a new location using the given coordinates, then uses reverse geocoding to obtain the address,
     * and finally creates a new category using the provided category name and obtained address.
     */
    private void createLocationAndCategory(double latitude, double longitude, String categoryName) {
        locationApiManager.createLocation(latitude, longitude, new Callback<LocationCreateResponse>() {
            @Override
            public void onResponse(@NonNull Call<LocationCreateResponse> call,
                                   @NonNull Response<LocationCreateResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getLocation() != null) {
                    int locationId = Integer.parseInt(response.body().getLocation().getId());
                    Timber.i("Created new location with ID: %d", locationId);
                    // Get address via reverse geocoding
                    getAddressFromCoordinates(latitude, longitude, address -> {
                        createCategory(locationId, categoryName, address);
                    });
                } else {
                    Toast.makeText(MainActivity.this, "Location creation failed", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onFailure(@NonNull Call<LocationCreateResponse> call, @NonNull Throwable t) {
                Timber.e(t, "Failed to create location");
                Toast.makeText(MainActivity.this, "Network error while creating location", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    /**
     * Creates a new category linked to the given location ID using the provided category name and address.
     */
    private void createCategory(int locationId, String categoryName, String address) {
        CategoryRequest request = new CategoryRequest(
                categoryName,
                address,
                5.0,
                locationId,
                1
        );

        apiService.createCategory(request).enqueue(new Callback<CategoryResponse>() {
            @Override
            public void onResponse(@NonNull Call<CategoryResponse> call,
                                   @NonNull Response<CategoryResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    selectedCategoryId = Integer.parseInt(response.body().getId());
                    Timber.i("Created new category with ID: %d", selectedCategoryId);
                    launchCamera();
                } else {
                    Toast.makeText(MainActivity.this, "Category creation failed", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onFailure(@NonNull Call<CategoryResponse> call, @NonNull Throwable t) {
                Timber.e(t, "Failed to create category");
                Toast.makeText(MainActivity.this, "Network error while creating category", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    /**
     * Uses Android's Geocoder to convert latitude and longitude into a human-readable address.
     * Calls the provided listener with the obtained address, or "Unknown Address" on failure.
     */
    private void getAddressFromCoordinates(double latitude, double longitude, OnAddressObtainedListener listener) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String addressText = address.getAddressLine(0);
                listener.onAddressObtained(addressText);
            } else {
                listener.onAddressObtained("Unknown Address");
            }
        } catch (IOException e) {
            Timber.e(e, "Reverse geocoding failed");
            listener.onAddressObtained("Unknown Address");
        }
    }

    /**
     * Launches CameraActivity. When the image is captured, onActivityResult is called,
     * which then passes the image path and category ID to ApiActivity for uploading.
     */
    private void launchCamera() {
        Intent cameraIntent = new Intent(MainActivity.this, CameraActivity.class);
        PackageManager packageManager = getPackageManager();
        if (cameraIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Timber.d("onActivityResult called with requestCode: %d, resultCode: %d", requestCode, resultCode);

        if (requestCode == CAMERA_REQUEST_CODE) {
            if (data != null && resultCode == RESULT_OK) {
                String imagePath = data.getStringExtra("image_path");
                // Pass the image path and selectedCategoryId to ApiActivity for uploading.
                Intent intent = new Intent(MainActivity.this, ApiActivity.class);
                intent.putExtra("image_path", imagePath);
                intent.putExtra("category_id", selectedCategoryId);
                startActivity(intent);
            } else {
                Toast.makeText(MainActivity.this, "Camera activity did not finish properly or no image data was returned.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
