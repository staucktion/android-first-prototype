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
import com.example.staucktion.api.RetrofitClient;
import com.example.staucktion.managers.LocationApiManager;
import com.example.staucktion.models.CategoryRequest;
import com.example.staucktion.models.CategoryResponse;
import com.example.staucktion.models.LocationCreateResponse;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
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
class StatusEnum { public static final int WAIT = 2; }

public class MainActivity extends AppCompatActivity {
    private static final int REQ_LOCATION           = 101;
    private static final int REQ_CAMERA_STARTUP     = 102;
    private static final int REQ_CAMERA_ON_OPEN     = 103;
    private static final int CAMERA_REQ_CODE        = 104;

    private ApiService                     apiService;
    private LocationManager                locationManager;
    private FusedLocationProviderClient    fusedLocationClient;
    private LocationApiManager             locationApiManager;

    private AutoCompleteTextView           categorySpinner;
    private MaterialTextView               noCategoryWarning;
    private MaterialButton                 createCategoryBtn;
    private TextView                       textAvatar;

    private double                         currentLatitude, currentLongitude;
    private int                            selectedCategoryId = -1;
    private List<CategoryResponse>         loadedCategories;

    private final BroadcastReceiver gpsToggleReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                sendBroadcast(new Intent("com.example.staucktion.KILL_CAMERA_ACTIVITY"));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Timber.plant(new Timber.DebugTree());

        apiService           = RetrofitClient.getInstance().create(ApiService.class);
        locationManager      = (LocationManager) getSystemService(LOCATION_SERVICE);
        fusedLocationClient  = LocationServices.getFusedLocationProviderClient(this);
        locationApiManager   = new LocationApiManager();

        setupToolbar();
        bindViews();
        registerReceiver(gpsToggleReceiver,
                new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));

        // 1) Request LOCATION
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQ_LOCATION);
        } else {
            onLocationPermissionGranted();
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        textAvatar = toolbar.findViewById(R.id.textAvatar);
        textAvatar.setOnClickListener(this::showAvatarPopup);
        toolbar.findViewById(R.id.logoutIcon)
                .setOnClickListener(v -> performLogout());
        loadUserProfile();
    }

    private void bindViews() {
        categorySpinner   = findViewById(R.id.categoryAutoCompleteTextView);
        noCategoryWarning = findViewById(R.id.noCategoryWarning);
        createCategoryBtn = findViewById(R.id.createCategoryButton);
        Button openCameraBtn = findViewById(R.id.openCamerabtn);

        createCategoryBtn.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{ Manifest.permission.CAMERA },
                        REQ_CAMERA_STARTUP);
            } else {
                promptForCategoryNameAndCreateCategory();
            }
        });

        openCameraBtn.setOnClickListener(v -> {
            if (selectedCategoryId < 0) {
                Toast.makeText(this, "Select or create a category first.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(this, "Enable GPS to take photos.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{ Manifest.permission.CAMERA },
                        REQ_CAMERA_ON_OPEN);
            } else {
                launchCamera();
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void onLocationPermissionGranted() {
        // start location → then immediately request CAMERA
        LocationRequest req = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5_000)
                .setFastestInterval(2_000);

        fusedLocationClient.requestLocationUpdates(req, new LocationCallback() {
            @Override public void onLocationResult(@NonNull LocationResult result) {
                fusedLocationClient.removeLocationUpdates(this);
                if (result.getLastLocation() != null) {
                    currentLatitude  = result.getLastLocation().getLatitude();
                    currentLongitude = result.getLastLocation().getLongitude();
                    loadApprovedCategories();
                } else {
                    showLocationErrorDialog();
                }
            }
        }, Looper.getMainLooper());

        // now ask CAMERA once
        ActivityCompat.requestPermissions(this,
                new String[]{ Manifest.permission.CAMERA },
                REQ_CAMERA_STARTUP);
    }

    private void loadApprovedCategories() {
        apiService.getApprovedCategoriesByCoordinates(
                currentLatitude, currentLongitude, "APPROVE"
        ).enqueue(new Callback<List<CategoryResponse>>() {
            @Override public void onResponse(Call<List<CategoryResponse>> call,
                                             Response<List<CategoryResponse>> res) {
                if (res.isSuccessful() && res.body()!=null) {
                    loadedCategories = res.body();
                    List<String> names = new ArrayList<>();
                    for (CategoryResponse c : loadedCategories) {
                        names.add(c.getName());
                    }
                    categorySpinner.setAdapter(
                            new ArrayAdapter<>(MainActivity.this,
                                    android.R.layout.simple_dropdown_item_1line,
                                    names));
                    noCategoryWarning.setVisibility(
                            names.isEmpty() ? View.VISIBLE : View.GONE);
                } else {
                    noCategoryWarning.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onFailure(Call<List<CategoryResponse>> call,
                                            Throwable t) {
                Toast.makeText(MainActivity.this,
                        "Failed to load categories", Toast.LENGTH_SHORT).show();
                noCategoryWarning.setVisibility(View.VISIBLE);
            }
        });
    }

    private void promptForCategoryNameAndCreateCategory() {
        View dialog = LayoutInflater.from(this)
                .inflate(R.layout.dialog_category, null);
        EditText box = dialog.findViewById(R.id.editCategoryName);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Enter Category Name")
                .setView(dialog)
                .setPositiveButton("OK", (d,w) -> {
                    String name = box.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Name cannot be empty",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        createLocationAndCategory(name);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createLocationAndCategory(String name) {
        locationApiManager.createLocation(
                currentLatitude, currentLongitude,
                new Callback<LocationCreateResponse>() {
                    @Override public void onResponse(
                            Call<LocationCreateResponse> call,
                            Response<LocationCreateResponse> res) {
                        if (res.isSuccessful()
                                && res.body()!=null
                                && res.body().getLocation()!=null) {
                            int locId = Integer.parseInt(
                                    res.body().getLocation().getId());
                            try {
                                List<Address> addrs = new Geocoder(
                                        MainActivity.this, Locale.getDefault())
                                        .getFromLocation(
                                                currentLatitude, currentLongitude, 1);
                                String address = addrs.isEmpty()
                                        ? "Unknown"
                                        : addrs.get(0).getAddressLine(0);
                                createCategory(locId, name, address);
                            } catch (IOException e) {
                                createCategory(locId, name, "Unknown");
                            }
                        } else {
                            Toast.makeText(MainActivity.this,
                                    "Location creation failed",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override public void onFailure(
                            Call<LocationCreateResponse> call, Throwable t) {
                        Toast.makeText(MainActivity.this,
                                "Network error", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void createCategory(int locationId, String name, String address) {
        apiService.createCategory(
                new CategoryRequest(name, address, 5.0, locationId, StatusEnum.WAIT)
        ).enqueue(new Callback<CategoryResponse>() {
            @Override public void onResponse(Call<CategoryResponse> call,
                                             Response<CategoryResponse> res) {
                if (res.isSuccessful() && res.body()!=null) {
                    selectedCategoryId =
                            Integer.parseInt(res.body().getId());
                    launchCamera();
                } else {
                    Toast.makeText(MainActivity.this,
                            "Category creation failed",
                            Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<CategoryResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this,
                        "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void launchCamera() {
        startActivityForResult(
                new Intent(this, CameraActivity.class),
                CAMERA_REQ_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        if (requestCode == REQ_LOCATION) {
            if (grantResults.length>0
                    && grantResults[0]==PackageManager.PERMISSION_GRANTED) {
                onLocationPermissionGranted();
            } else {
                Toast.makeText(this,
                        "Location permission required",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }

        if (requestCode == REQ_CAMERA_ON_OPEN) {
            if (grantResults.length>0
                    && grantResults[0]==PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                Toast.makeText(this,
                        "Camera permission required",
                        Toast.LENGTH_LONG).show();
            }
        }
        // REQ_CAMERA_STARTUP is only to prime the system dialog; no extra action needed
    }

    private void showLocationErrorDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Location unavailable")
                .setMessage("Could not get location. Retry?")
                .setPositiveButton("Retry", (d,w)-> onLocationPermissionGranted())
                .setNegativeButton("Exit",  (d,w)-> finish())
                .show();
    }

    private void showAvatarPopup(View anchor) {
        View popup = LayoutInflater.from(this)
                .inflate(R.layout.layout_avatar_popup, null);
        TextView name = popup.findViewById(R.id.popupFullName);
        ImageView pic = popup.findViewById(R.id.popupProfileImage);
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        name.setText(prefs.getString("userName","User"));
        String url = prefs.getString("userPhotoUrl","");
        if (!url.isEmpty()) Glide.with(this).load(url).into(pic);

        new PopupWindow(popup,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                true).showAsDropDown(anchor);
    }

    private void loadUserProfile() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String[] parts = prefs.getString("userName","?").split("\\s+");
        StringBuilder ab = new StringBuilder();
        for (String p : parts) ab.append(p.charAt(0));
        textAvatar.setText(ab.toString().toUpperCase());
    }

    private void performLogout() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit().clear().apply();
        GoogleSignInClient client = GoogleSignIn.getClient(this,
                new com.google.android.gms.auth.api.signin
                        .GoogleSignInOptions.Builder(
                        com.google.android.gms.auth.api.signin
                                .GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail().build()
        );
        client.signOut().addOnCompleteListener(t-> {
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        });
    }
}
