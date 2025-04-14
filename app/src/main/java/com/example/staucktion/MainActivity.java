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
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.staucktion.api.ApiService;
import com.example.staucktion.api.OnAddressObtainedListener;
import com.example.staucktion.api.RetrofitClient;
import com.example.staucktion.managers.LocationApiManager;
import com.example.staucktion.models.CategoryRequest;
import com.example.staucktion.models.CategoryResponse;
import com.example.staucktion.models.LocationCreateResponse;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;
import com.onesignal.OneSignal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

// Assume you have a StatusEnum defined somewhere:
class StatusEnum {
    public static final int WAIT = 2; // WAIT status value
}

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 101;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 102;

    private LocationManager locationManager;
    private FusedLocationProviderClient fusedLocationClient;

    // Coordinates
    private double currentLatitude = 0;
    private double currentLongitude = 0;

    // Category and API-related variables
    private int selectedCategoryId = -1;
    private List<CategoryResponse> loadedCategories = new ArrayList<>();
    private ApiService apiService;
    private LocationApiManager locationApiManager;
    private AutoCompleteTextView categoryAutoCompleteTextView;

    // UI elements for category guidance
    private MaterialTextView noCategoryWarning;
    private MaterialButton createCategoryButton;  // Always visible

    // Flag for location issues during camera session
    private boolean locationWasDisabled = false;

    private GoogleSignInClient googleSignInClient;

    // Using a text avatar (instead of an image) and logout icon on the toolbar.
    private TextView textAvatar;
    private View logoutIcon;

    // Category refresh handler and runnable
    private Handler categoryHandler = new Handler(Looper.getMainLooper());
    private Runnable categoryRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshCategories();
            categoryHandler.postDelayed(this, 10000);
        }
    };

    // BroadcastReceiver for location changes
    private final BroadcastReceiver locationChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationWasDisabled = true;
                SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                prefs.edit().putBoolean("gpsDisabledDuringCamera", true).apply();

                Intent killIntent = new Intent("com.example.staucktion.KILL_CAMERA_ACTIVITY");
                sendBroadcast(killIntent);
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(locationChangeReceiver, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
        categoryHandler.post(categoryRefreshRunnable);
        refreshCategories();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(locationChangeReceiver);
        categoryHandler.removeCallbacks(categoryRefreshRunnable);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize OneSignal push notifications (replace with your app ID)
        OneSignal.initWithContext(this, "5dafc689-25b8-4756-8c78-a31e6a541e51");

        Timber.plant(new Timber.DebugTree());
        Timber.i("Starting MainActivity");

        // Get the toolbar from the layout and retrieve toolbar elements.
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        textAvatar = toolbar.findViewById(R.id.textAvatar);
        logoutIcon = toolbar.findViewById(R.id.logoutIcon);

        // Load the user's profile and set text avatar.
        loadUserProfile();
        textAvatar.setOnClickListener(v -> showAvatarPopup(v));

        // Set up logout functionality.
        logoutIcon.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            prefs.edit().clear().apply();

            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.web_client_id))
                    .requestEmail()
                    .build();
            googleSignInClient = GoogleSignIn.getClient(MainActivity.this, gso);

            googleSignInClient.signOut().addOnCompleteListener(task -> {
                Toast.makeText(MainActivity.this, "Logged out", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
                finish();
            });
        });

        // Check token validity.
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

        // Initialize API service, location manager, and fused location client.
        apiService = RetrofitClient.getInstance().create(ApiService.class);
        locationApiManager = new LocationApiManager();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Reference AutoCompleteTextView, warning TextView, Create Category button, and Open Camera button.
        categoryAutoCompleteTextView = findViewById(R.id.categoryAutoCompleteTextView);
        noCategoryWarning = findViewById(R.id.noCategoryWarning);
        createCategoryButton = findViewById(R.id.createCategoryButton);
        Button openCameraBtn = findViewById(R.id.openCamerabtn);

        // Always allow the user to create a new category.
        createCategoryButton.setOnClickListener(v ->
                promptForCategoryNameAndCreateCategory(currentLatitude, currentLongitude)
        );

        // Check and request location permissions.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getLastLocation();
        }

        // Handle Open Camera button click.
        openCameraBtn.setOnClickListener(v -> {
            // First, check GPS.
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(MainActivity.this, "GPS is turned off. Please enable GPS to use the camera.", Toast.LENGTH_LONG).show();
                return;
            }
            // Then, check camera permissions.
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.CAMERA)) {
                    new MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle("Camera Permission Needed")
                            .setMessage("This app needs camera access to take photos. Please grant the permission.")
                            .setPositiveButton("Allow", (dialog, which) ->
                                    ActivityCompat.requestPermissions(MainActivity.this,
                                            new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE)
                            )
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
                // If camera permission is granted, check if a category is selected.
                String selectedName = categoryAutoCompleteTextView.getText().toString().trim();
                if (selectedName.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Please first select or create a new category.", Toast.LENGTH_SHORT).show();
                    return;
                } else {
                    // Attempt to find the selected category in our loaded categories.
                    selectedCategoryId = -1;
                    for (CategoryResponse cat : loadedCategories) {
                        if (cat.getName().equalsIgnoreCase(selectedName)) {
                            selectedCategoryId = Integer.parseInt(cat.getId());
                            break;
                        }
                    }
                    if (selectedCategoryId != -1) {
                        launchCamera();
                        return;
                    } else {
                        // If the text is non-empty but doesn’t match any loaded category.
                        Toast.makeText(MainActivity.this, "Please first select or create a valid category.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }
        });
    }

    /**
     * Loads the user's profile from SharedPreferences and sets the text avatar abbreviation.
     */
    private void loadUserProfile() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String fullName = prefs.getString("userName", "John Doe");
        String abbreviation = getAbbreviation(fullName);
        textAvatar.setText(abbreviation);
    }

    /**
     * Returns a two-letter abbreviation for a full name.
     */
    private String getAbbreviation(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "?";
        }
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder abbreviation = new StringBuilder();
        for (String part : parts) {
            abbreviation.append(part.substring(0, 1).toUpperCase());
        }
        return abbreviation.toString();
    }

    /**
     * Displays a PopupWindow below the text avatar showing the user's full name and profile image.
     */
    private void showAvatarPopup(View anchorView) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View popupView = inflater.inflate(R.layout.layout_avatar_popup, null);
        TextView popupFullName = popupView.findViewById(R.id.popupFullName);
        ImageView popupProfileImage = popupView.findViewById(R.id.popupProfileImage);
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String fullName = prefs.getString("userName", "John Doe");
        String photoUrl = prefs.getString("userPhotoUrl", "");
        popupFullName.setText(fullName);
        if (!photoUrl.isEmpty()) {
            Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.default_profile)
                    .into(popupProfileImage);
        } else {
            popupProfileImage.setImageResource(R.drawable.default_profile);
        }
        PopupWindow popupWindow = new PopupWindow(
                popupView,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.showAsDropDown(anchorView, 0, 0);
    }

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
                        MaterialTextView noCategoryWarning = findViewById(R.id.noCategoryWarning);
                        if (response.isSuccessful() && response.body() != null) {
                            List<CategoryResponse> categories = response.body();
                            if (!categories.isEmpty()) {
                                loadedCategories = categories;
                                updateSpinnerWithCategories(loadedCategories);
                                noCategoryWarning.setVisibility(View.GONE);
                            } else {
                                noCategoryWarning.setVisibility(View.VISIBLE);
                                updateSpinnerWithCategories(new ArrayList<>());
                            }
                        } else {
                            noCategoryWarning.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<CategoryResponse>> call, @NonNull Throwable t) {
                        Timber.e(t, "Error loading categories");
                        Toast.makeText(MainActivity.this, "Failed to load categories.", Toast.LENGTH_SHORT).show();
                        MaterialTextView noCategoryWarning = findViewById(R.id.noCategoryWarning);
                        noCategoryWarning.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void updateSpinnerWithCategories(List<CategoryResponse> categories) {
        List<String> names = new ArrayList<>();
        for (CategoryResponse cat : categories) {
            names.add(cat.getName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line,
                names);
        adapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
        categoryAutoCompleteTextView.setAdapter(adapter);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, Objects.requireNonNull(permissions), grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastLocation();
            } else {
                Toast.makeText(this, "Location permission required.", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkCategoryAtCoordinates(currentLatitude, currentLongitude);
            } else {
                Toast.makeText(MainActivity.this, "Camera permission required.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void checkCategoryAtCoordinates(double latitude, double longitude) {
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

    private void createLocationAndCategory(double latitude, double longitude, String categoryName) {
        locationApiManager.createLocation(latitude, longitude, new Callback<LocationCreateResponse>() {
            @Override
            public void onResponse(@NonNull Call<LocationCreateResponse> call,
                                   @NonNull Response<LocationCreateResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getLocation() != null) {
                    int locationId = Integer.parseInt(response.body().getLocation().getId());
                    Timber.i("Created new location with ID: %d", locationId);
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

    private void createCategory(int locationId, String categoryName, String address) {
        int statusId = StatusEnum.WAIT;
        CategoryRequest request = new CategoryRequest(categoryName, address, 5.0, locationId, statusId);
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

    private void launchCamera() {
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

    private void refreshCategories() {
        if (currentLatitude != 0 && currentLongitude != 0) {
            loadApprovedCategories(currentLatitude, currentLongitude);
        } else {
            getLastLocation();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                boolean gpsStatusAtStart = prefs.getBoolean("gpsStatusOnCameraStart", true);
                boolean gpsDisabledDuringCamera = prefs.getBoolean("gpsDisabledDuringCamera", false);
                boolean gpsStatusNow = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                if (!gpsStatusAtStart || locationWasDisabled || gpsDisabledDuringCamera || !gpsStatusNow) {
                    Toast.makeText(MainActivity.this,
                            "GPS was disabled during photo capture. Upload failed. Please enable GPS and try again.",
                            Toast.LENGTH_LONG).show();
                    categoryAutoCompleteTextView.setText("");
                    return;
                }
                String imagePath = data.getStringExtra("image_path");
                Intent intent = new Intent(MainActivity.this, ApiActivity.class);
                intent.putExtra("image_path", imagePath);
                intent.putExtra("category_id", selectedCategoryId);
                startActivity(intent);
                refreshCategories();
            } else {
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