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
import android.os.Build;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

class StatusEnum {
    public static final int WAIT = 2;
}

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_REQUEST_CODE                = 100;
    private static final int LOCATION_PERMISSION_REQUEST_CODE   = 101;
    private static final int CAMERA_PERMISSION_REQUEST_CODE     = 102;

    private LocationManager locationManager;
    private FusedLocationProviderClient fusedLocationClient;
    private double currentLatitude, currentLongitude;

    private int selectedCategoryId = -1;
    private List<CategoryResponse> loadedCategories = new ArrayList<>();
    private ApiService apiService;
    private LocationApiManager locationApiManager;

    private AutoCompleteTextView categoryAutoCompleteTextView;
    private MaterialTextView       noCategoryWarning;
    private MaterialButton         createCategoryButton;
    private TextView               textAvatar;
    private GoogleSignInClient     googleSignInClient;

    private final Handler categoryRefreshHandler = new Handler(Looper.getMainLooper());
    private final Handler categoryHandler = new Handler(Looper.getMainLooper());

    private final Runnable categoryRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshCategories();
            // here `this` refers to the Runnable
            categoryHandler.postDelayed(this, 10_000);
        }
    };

    private final BroadcastReceiver locationChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                prefs.edit().putBoolean("gpsDisabledDuringCamera", true).apply();
                sendBroadcast(new Intent("com.example.staucktion.KILL_CAMERA_ACTIVITY"));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Timber.plant(new Timber.DebugTree());
        Timber.i("Starting MainActivity");

        // 1) Toolbar & avatar popup
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        textAvatar = toolbar.findViewById(R.id.textAvatar);
        textAvatar.setOnClickListener(this::showAvatarPopup);
        loadUserProfile();
        toolbar.findViewById(R.id.logoutIcon).setOnClickListener(v -> performLogout());

        // 2) Validate JWT (else redirect to LoginActivity)
        if (!validateToken()) {
            return;   // we’ll never hit the notification prompt if not logged in
        }

        // 4) Init services
        apiService          = RetrofitClient.getInstance().create(ApiService.class);
        locationApiManager  = new LocationApiManager();
        locationManager     = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 5) Find all UI views
        categoryAutoCompleteTextView = findViewById(R.id.categoryAutoCompleteTextView);
        noCategoryWarning            = findViewById(R.id.noCategoryWarning);
        createCategoryButton         = findViewById(R.id.createCategoryButton);
        Button openCameraBtn         = findViewById(R.id.openCamerabtn);

        // 6) Hooks
        createCategoryButton.setOnClickListener(v ->
                promptForCategoryNameAndCreateCategory(currentLatitude, currentLongitude)
        );
        openCameraBtn.setOnClickListener(v -> handleOpenCamera());

        // 7) Location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{ Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION },
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        } else {
            getLastLocation();
        }

        // 8) Start periodic category refresh
        categoryHandler.postDelayed(categoryRefreshRunnable, 10_000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(locationChangeReceiver,
                new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(locationChangeReceiver);
        categoryRefreshHandler.removeCallbacks(categoryRefreshRunnable);
    }

    private void handleOpenCamera() {
        // 0) Make sure they’ve selected or created a category
        if (selectedCategoryId < 0) {
            Toast.makeText(this,
                    "Please select or create a category before taking a photo.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this,
                    "GPS is turned off. Please enable GPS to use the camera.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{ Manifest.permission.CAMERA },
                    CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            checkCategoryAndLaunchCamera();
        }
    }

    @SuppressLint("MissingPermission")
    private void getLastLocation() {
        LocationRequest req = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5000)
                .setFastestInterval(2000);

        fusedLocationClient.requestLocationUpdates(req,
                new LocationCallback() {
                    @Override
                    public void onLocationResult(@NonNull LocationResult result) {
                        fusedLocationClient.removeLocationUpdates(this);
                        if (result.getLastLocation() != null) {
                            currentLatitude  = result.getLastLocation().getLatitude();
                            currentLongitude = result.getLastLocation().getLongitude();
                            Timber.i("Location: %f, %f", currentLatitude, currentLongitude);
                            loadApprovedCategories(currentLatitude, currentLongitude);
                        } else {
                            showLocationErrorDialog();
                        }
                    }
                }, Looper.getMainLooper());
    }

    private void loadApprovedCategories(double lat, double lng) {
        apiService.getApprovedCategoriesByCoordinates(lat, lng, "APPROVE")
                .enqueue(new Callback<List<CategoryResponse>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<CategoryResponse>> call,
                                           @NonNull Response<List<CategoryResponse>> res) {
                        if (res.isSuccessful() && res.body() != null) {
                            loadedCategories = res.body();
                            updateSpinnerWithCategories(loadedCategories);
                            noCategoryWarning.setVisibility(
                                    loadedCategories.isEmpty() ? View.VISIBLE : View.GONE
                            );
                        } else {
                            noCategoryWarning.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<CategoryResponse>> call,
                                          @NonNull Throwable t) {
                        Timber.e(t, "Error loading themes");
                        Toast.makeText(MainActivity.this,
                                "Failed to load themes.", Toast.LENGTH_SHORT).show();
                        noCategoryWarning.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void updateSpinnerWithCategories(List<CategoryResponse> cats) {
        List<String> names = new ArrayList<>();
        for (CategoryResponse c : cats) names.add(c.getName());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, names);
        categoryAutoCompleteTextView.setAdapter(adapter);

        categoryAutoCompleteTextView.setOnItemClickListener((parent, view, position, id) -> {
            CategoryResponse picked = cats.get(position);
            try {
                selectedCategoryId = Integer.parseInt(picked.getId());
                noCategoryWarning.setVisibility(View.GONE);
                Timber.d("Category picked: %s (id=%d)", picked.getName(), selectedCategoryId);
            } catch (NumberFormatException e) {
                Timber.e(e, "Invalid category id: %s", picked.getId());
            }
        });
    }

    private void checkCategoryAndLaunchCamera() {
        if (currentLatitude == 0 && currentLongitude == 0) {
            getLastLocation();
            return;
        }

        apiService.getApprovedCategoriesByCoordinates(
                        currentLatitude, currentLongitude, "APPROVE")
                .enqueue(new Callback<List<CategoryResponse>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<CategoryResponse>> call,
                                           @NonNull Response<List<CategoryResponse>> res) {
                        if (res.isSuccessful() && res.body() != null
                                && !res.body().isEmpty()) {
                            try {
                                selectedCategoryId =
                                        Integer.parseInt(res.body().get(0).getId());
                                launchCamera();
                            } catch (NumberFormatException e) {
                                Timber.e(e, "Invalid category ID");
                                promptForCategoryNameAndCreateCategory(
                                        currentLatitude, currentLongitude);
                            }
                        } else {
                            promptForCategoryNameAndCreateCategory(
                                    currentLatitude, currentLongitude);
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<List<CategoryResponse>> call,
                                          @NonNull Throwable t) {
                        Timber.e(t, "Error checking themes");
                        Toast.makeText(MainActivity.this,
                                "Network error.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void promptForCategoryNameAndCreateCategory(
            double lat, double lng) {
        View         view  = LayoutInflater.from(this)
                .inflate(R.layout.dialog_category, null);
        EditText     input = view.findViewById(R.id.editCategoryName);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Enter Category Name")
                .setView(view)
                .setPositiveButton("OK", (dlg, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this,
                                "Category name cannot be empty",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        createLocationAndCategory(lat, lng, name);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createLocationAndCategory(
            double lat, double lng, String name) {
        locationApiManager.createLocation(lat, lng,
                new Callback<LocationCreateResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<LocationCreateResponse> call,
                                           @NonNull Response<LocationCreateResponse> res) {
                        if (res.isSuccessful()
                                && res.body() != null
                                && res.body().getLocation() != null) {
                            try {
                                int locationId = Integer.parseInt(
                                        res.body().getLocation().getId());
                                getAddressFromCoordinates(lat, lng, address ->
                                        createCategory(locationId, name, address)
                                );
                            } catch (NumberFormatException e) {
                                Toast.makeText(MainActivity.this,
                                        "Error creating location.",
                                        Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(MainActivity.this,
                                    "Location creation failed",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<LocationCreateResponse> call,
                                          @NonNull Throwable t) {
                        Timber.e(t, "Failed to create location");
                        Toast.makeText(MainActivity.this,
                                "Network error.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void createCategory(int locationId,
                                String categoryName,
                                String address) {
        CategoryRequest req =
                new CategoryRequest(categoryName, address, 5.0,
                        locationId, StatusEnum.WAIT);

        apiService.createCategory(req)
                .enqueue(new Callback<CategoryResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<CategoryResponse> call,
                                           @NonNull Response<CategoryResponse> res) {
                        if (res.isSuccessful() && res.body() != null) {
                            try {
                                selectedCategoryId =
                                        Integer.parseInt(res.body().getId());
                                launchCamera();
                            } catch (NumberFormatException e) {
                                Toast.makeText(MainActivity.this,
                                        "Error parsing category ID",
                                        Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(MainActivity.this,
                                    "Category creation failed",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<CategoryResponse> call,
                                          @NonNull Throwable t) {
                        Timber.e(t, "Failed to create category");
                        Toast.makeText(MainActivity.this,
                                "Network error.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void getAddressFromCoordinates(
            double lat, double lng,
            OnAddressObtainedListener listener) {
        try {
            List<Address> addresses = new Geocoder(this, Locale.getDefault())
                    .getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                listener.onAddressObtained(
                        addresses.get(0).getAddressLine(0));
            } else {
                listener.onAddressObtained("Unknown Address");
            }
        } catch (IOException e) {
            Timber.e(e, "Reverse geocoding failed");
            listener.onAddressObtained("Unknown Address");
        }
    }

    private void launchCamera() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit()
                .putBoolean("gpsDisabledDuringCamera", false)
                .putBoolean("gpsStatusOnCameraStart",
                        locationManager.isProviderEnabled(
                                LocationManager.GPS_PROVIDER))
                .apply();

        startActivityForResult(
                new Intent(this, CameraActivity.class),
                CAMERA_REQUEST_CODE
        );
    }

    private void refreshCategories() {
        loadApprovedCategories(currentLatitude, currentLongitude);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0
                        && grantResults[0]
                        == PackageManager.PERMISSION_GRANTED) {
                    getLastLocation();
                } else {
                    Toast.makeText(this,
                            "Location permission required.",
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;

            case CAMERA_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0
                        && grantResults[0]
                        == PackageManager.PERMISSION_GRANTED) {
                    checkCategoryAndLaunchCamera();
                } else {
                    Toast.makeText(this,
                            "Camera permission required.",
                            Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private void showLocationErrorDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Location Not Available")
                .setMessage("Could not obtain location. Please try again.")
                .setPositiveButton("Retry", (d,i) -> getLastLocation())
                .setNegativeButton("Exit", (d,i) -> finish())
                .show();
    }

    private void showAvatarPopup(View anchor) {
        View popupView = LayoutInflater.from(this)
                .inflate(R.layout.layout_avatar_popup, null);
        TextView    fullNameView    = popupView.findViewById(R.id.popupFullName);
        ImageView   profileImage    = popupView.findViewById(R.id.popupProfileImage);
        SharedPreferences prefs    = getSharedPreferences("AppPrefs", MODE_PRIVATE);

        fullNameView.setText(prefs.getString("userName", "John Doe"));
        String photoUrl = prefs.getString("userPhotoUrl", "");
        if (!photoUrl.isEmpty()) {
            Glide.with(this).load(photoUrl).into(profileImage);
        }

        new PopupWindow(popupView,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                true)
                .showAsDropDown(anchor);
    }

    private void loadUserProfile() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        textAvatar.setText(
                getAbbreviation(prefs.getString("userName", "John Doe"))
        );
    }

    private String getAbbreviation(String fullName) {
        if (fullName == null || fullName.isEmpty()) return "?";
        StringBuilder abbr = new StringBuilder();
        for (String part : fullName.trim().split("\\s+")) {
            abbr.append(part.charAt(0));
        }
        return abbr.toString().toUpperCase();
    }

    private boolean validateToken() {
        SharedPreferences prefs  = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String            token  = prefs.getString("appToken", null);
        long              expiry = prefs.getLong("appTokenExpiry", 0);

        if (token == null || expiry == 0 || System.currentTimeMillis() > expiry) {
            prefs.edit().clear().apply();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return false;
        }

        RetrofitClient.getInstance().setAuthToken(token);
        return true;
    }

    private void performLogout() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit().clear().apply();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.web_client_id))
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);
        googleSignInClient.signOut()
                .addOnCompleteListener(task -> {
                    Toast.makeText(this,
                            "Logged out", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                });
    }

    @Override
    protected void onActivityResult(int requestCode,
                                    int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_REQUEST_CODE) {
            handleCameraResult(resultCode, data);
        }
    }

    private void handleCameraResult(int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data != null) {
            SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            boolean gpsStart    = prefs.getBoolean("gpsStatusOnCameraStart", true);
            boolean gpsDisabled = prefs.getBoolean("gpsDisabledDuringCamera", false);
            boolean gpsNow      = locationManager.isProviderEnabled(
                    LocationManager.GPS_PROVIDER);

            if (!gpsStart || gpsDisabled || !gpsNow) {
                Toast.makeText(this,
                        "GPS was disabled during photo capture. Upload failed.",
                        Toast.LENGTH_LONG).show();
                categoryAutoCompleteTextView.setText("");
                return;
            }

            String path = data.getStringExtra("image_path");
            Intent intent = new Intent(this, ApiActivity.class);
            intent.putExtra("image_path", path);
            intent.putExtra("category_id", selectedCategoryId);
            startActivity(intent);
            refreshCategories();
        } else {
            Toast.makeText(this,
                    "Camera activity failed or cancelled.", Toast.LENGTH_SHORT).show();
        }
    }
}
