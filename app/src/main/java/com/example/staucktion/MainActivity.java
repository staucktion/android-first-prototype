package com.example.staucktion;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

import android.os.Handler;
import android.os.Looper;

// Assume you have a StatusEnum defined somewhere, for example:
class StatusEnum {
    public static final int WAIT = 2; // WAIT status value
}

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 101;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 102;

    private LocationManager locationManager;
    private FusedLocationProviderClient fusedLocationClient;

    // Dynamically obtained coordinates
    private double currentLatitude = 0;
    private double currentLongitude = 0;

    // Holds the selected category ID (if one is chosen)
    private int selectedCategoryId = -1;
    // List to hold the loaded categories so we can map names to IDs
    private List<CategoryResponse> loadedCategories = new ArrayList<>();

    private ApiService apiService;
    private LocationApiManager locationApiManager;
    private AutoCompleteTextView categoryAutoCompleteTextView;

    // Flag to indicate if location was ever disabled during the camera session.
    // This flag is now also persisted in SharedPreferences.
    private boolean locationWasDisabled = false;

    // Add a Handler and Runnable for periodic category refresh
    private Handler categoryHandler = new Handler(Looper.getMainLooper());
    private Runnable categoryRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshCategories();
            // Schedule the next refresh in 10 seconds (adjust the delay as needed)
            categoryHandler.postDelayed(this, 10000);
        }
    };

    // BroadcastReceiver for location changes remains unchanged
    private final BroadcastReceiver locationChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                // GPS was disabled; update the flag and broadcast a kill intent.
                locationWasDisabled = true;
                SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                prefs.edit().putBoolean("gpsDisabledDuringCamera", true).apply();

                Intent killIntent = new Intent("com.example.staucktion.KILL_CAMERA_ACTIVITY");
                sendBroadcast(killIntent);
            }
        }
    };

    // Additionally, refresh categories every time the activity resumes:
    @Override
    protected void onResume() {
        super.onResume();
        // Register the location change receiver
        registerReceiver(locationChangeReceiver, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));

        // Start the periodic category refresh
        categoryHandler.post(categoryRefreshRunnable);

        // Optionally, do an immediate refresh
        refreshCategories();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the location change receiver
        unregisterReceiver(locationChangeReceiver);
        // Remove pending callbacks to avoid memory leaks or unwanted updates when activity is paused
        categoryHandler.removeCallbacks(categoryRefreshRunnable);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Timber.plant(new Timber.DebugTree());
        Timber.i("Starting MainActivity");

        // Token check (if no valid token, redirect to LoginActivity)
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String appToken = prefs.getString("appToken", null);
        long tokenExpiry = prefs.getLong("appTokenExpiry", 0);
        if (appToken == null || tokenExpiry == 0 || System.currentTimeMillis() > tokenExpiry) {
            prefs.edit().clear().apply();
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

        // Reference to AutoCompleteTextView in layout
        categoryAutoCompleteTextView = findViewById(R.id.categoryAutoCompleteTextView);
        Button openCameraBtn = findViewById(R.id.openCamerabtn);
        Button profileBtn = findViewById(R.id.profilebtn);

        // Load location and approved categories
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getLastLocation();
        }

        // Open Camera button click
        openCameraBtn.setOnClickListener(v -> {
            // Check if GPS is enabled before proceeding
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(MainActivity.this, "GPS is turned off. Please enable GPS to use the camera.", Toast.LENGTH_LONG).show();
                return;
            }

            // First, check for camera permission
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.CAMERA)) {
                    new MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle("Camera Permission Needed")
                            .setMessage("This app needs camera access to take photos. Please grant the permission.")
                            .setPositiveButton("Allow", (dialog, which) -> ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE))
                            .setNegativeButton("Cancel", (dialog, which) -> {
                                dialog.dismiss();
                                Toast.makeText(MainActivity.this, "Camera permission is required.", Toast.LENGTH_SHORT).show();
                            })
                            .show();
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
                }
            } else {
                // Check if the user has selected a category from the dropdown
                String selectedName = categoryAutoCompleteTextView.getText().toString().trim();
                if (!selectedName.isEmpty()) {
                    // Look for the category in our loadedCategories list
                    for (CategoryResponse cat : loadedCategories) {
                        if (cat.getName().equalsIgnoreCase(selectedName)) {
                            selectedCategoryId = Integer.parseInt(cat.getId());
                            break;
                        }
                    }
                    if (selectedCategoryId != -1) {
                        launchCamera();
                        return;
                    }
                }
                // If no valid selection, prompt the user for a new category name.
                promptForCategoryNameAndCreateCategory(currentLatitude, currentLongitude);
            }
        });

        // Profile button
        profileBtn.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, ProfileActivity.class)));
    }

    /**
     * Retrieves the last known location from the fused location provider.
     */
    @SuppressLint("MissingPermission")
    private void getLastLocation() {
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5000)
                .setFastestInterval(2000);

        fusedLocationClient.requestLocationUpdates(locationRequest, new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                fusedLocationClient.removeLocationUpdates(this);

                if (locationResult != null && locationResult.getLastLocation() != null) {
                    currentLatitude = locationResult.getLastLocation().getLatitude();
                    currentLongitude = locationResult.getLastLocation().getLongitude();
                    Timber.i("Fresh location obtained: Lat=%f, Lng=%f", currentLatitude, currentLongitude);
                    loadApprovedCategories(currentLatitude, currentLongitude);
                } else {
                    Timber.w("Failed to obtain fresh location.");
                    showLocationErrorDialog();
                }
            }
        }, Looper.getMainLooper());
    }

    private void showLocationErrorDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Location Not Available")
                .setMessage("Could not obtain location. Please check your location services or try again.")
                .setPositiveButton("Retry", (dialog, which) -> {
                    dialog.dismiss();
                    getLastLocation();
                })
                .setNegativeButton("Go Back", (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .show();
    }

    private void loadApprovedCategories(double latitude, double longitude) {
        apiService.getApprovedCategoriesByCoordinates(latitude, longitude, "APPROVE")
                .enqueue(new Callback<List<CategoryResponse>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<CategoryResponse>> call,
                                           @NonNull Response<List<CategoryResponse>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            loadedCategories = response.body();
                            updateSpinnerWithCategories(loadedCategories);
                        } else {
                            Timber.e("Category response unsuccessful or empty.");
                            Toast.makeText(MainActivity.this, "No categories available for this location.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<CategoryResponse>> call, @NonNull Throwable t) {
                        Timber.e(t, "Error loading categories");
                        Toast.makeText(MainActivity.this, "Failed to load categories.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateSpinnerWithCategories(List<CategoryResponse> categories) {
        List<String> names = new ArrayList<>();
        for (CategoryResponse cat : categories) {
            names.add(cat.getName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, names);
        adapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
        categoryAutoCompleteTextView.setAdapter(adapter);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,
                Objects.requireNonNull(permissions),
                grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastLocation();
            } else {
                Toast.makeText(this, "Location permission required.", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Once camera permission is granted, proceed with checking category
                checkCategoryAtCoordinates(currentLatitude, currentLongitude);
            } else {
                Toast.makeText(MainActivity.this, "Camera permission required.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Checks via your backend if an existing category exists for the given coordinates.
     */
    private void checkCategoryAtCoordinates(double latitude, double longitude) {
        // Check if GPS is enabled before proceeding
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(MainActivity.this, "GPS is turned off. Please enable GPS to use the camera.", Toast.LENGTH_LONG).show();
            return;
        }

        apiService.getApprovedCategoriesByCoordinates(latitude, longitude, "APPROVE")
                .enqueue(new Callback<List<CategoryResponse>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<CategoryResponse>> call,
                                           @NonNull Response<List<CategoryResponse>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            // Pick the first approved category from the list
                            CategoryResponse approvedCategory = response.body().get(0);
                            selectedCategoryId = Integer.parseInt(approvedCategory.getId());
                            Timber.i("Found approved category with ID: %d", selectedCategoryId);
                            launchCamera();
                        } else {
                            Timber.i("No approved category found. Prompting for new location and category.");
                            promptForCategoryNameAndCreateCategory(latitude, longitude);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<CategoryResponse>> call, @NonNull Throwable t) {
                        Timber.e(t, "Error checking approved categories by coordinates");
                        Toast.makeText(MainActivity.this, "Network error while checking category", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Prompts the user for a category name using a custom dialog layout.
     * If the user enters a name, creates a new location and category.
     */
    private void promptForCategoryNameAndCreateCategory(final double latitude, final double longitude) {
        View customView = getLayoutInflater().inflate(R.layout.dialog_category, null);
        EditText input = customView.findViewById(R.id.editCategoryName);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Enter Category Name")
                .setView(customView)
                .setPositiveButton("OK", (dialog, which) -> {
                    String categoryName = input.getText().toString().trim();
                    if (categoryName.isEmpty()) {
                        Toast.makeText(MainActivity.this, "Category name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    createLocationAndCategory(latitude, longitude, categoryName);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel())
                .show();
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
                    // Get address via reverse geocoding and then create category
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
     * The status is set dynamically based on photo status (here we assume WAIT status).
     */
    private void createCategory(int locationId, String categoryName, String address) {
        int statusId = StatusEnum.WAIT;

        CategoryRequest request = new CategoryRequest(
                categoryName,
                address,
                5.0,
                locationId,
                statusId
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
     */
    private void getAddressFromCoordinates(double latitude, double longitude, OnAddressObtainedListener listener) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                Timber.i("Address obtained: %s", address.toString());
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
        // Reset GPS flags before starting the camera
        locationWasDisabled = false;
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit()
                .putBoolean("gpsDisabledDuringCamera", false)
                .putBoolean("gpsStatusOnCameraStart", locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                .apply();

        Intent cameraIntent = new Intent(MainActivity.this, CameraActivity.class);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
        }
    }

    // Add this method to clearly separate loading logic.
    private void refreshCategories() {
        if (currentLatitude != 0 && currentLongitude != 0) {
            loadApprovedCategories(currentLatitude, currentLongitude);
        } else {
            getLastLocation(); // Ensure we have fresh coordinates if needed.
        }
    }

    // Call refreshCategories() whenever camera or upload finishes successfully.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAMERA_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                // Perform the GPS flag checks as before.
                SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                boolean gpsStatusAtStart = prefs.getBoolean("gpsStatusOnCameraStart", true);
                boolean gpsDisabledDuringCamera = prefs.getBoolean("gpsDisabledDuringCamera", false);
                boolean gpsStatusNow = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

                if (!gpsStatusAtStart || locationWasDisabled || gpsDisabledDuringCamera || !gpsStatusNow) {
                    Toast.makeText(MainActivity.this,
                            "GPS was disabled during photo capture. Upload failed. Please enable GPS and try again.",
                            Toast.LENGTH_LONG).show();
                    categoryAutoCompleteTextView.setText(""); // reset UI if necessary
                    return;  // Stop here, prevent upload!
                }

                String imagePath = data.getStringExtra("image_path");
                Intent intent = new Intent(MainActivity.this, ApiActivity.class);
                intent.putExtra("image_path", imagePath);
                intent.putExtra("category_id", selectedCategoryId);
                startActivity(intent);

                // Immediately refresh categories when returning
                refreshCategories();
            } else {
                // Check if the cancellation was due to GPS being disabled.
                boolean gpsDisabled = data != null && data.getBooleanExtra("gps_disabled", false);
                if (gpsDisabled) {
                    Toast.makeText(MainActivity.this,
                            "GPS has been turned off during photo capture. This app requires GPS to ensure accurate location data. " +
                                    "Upload failed. Please enable GPS and try again.",
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, "Camera activity failed or canceled.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}
