package com.example.staucktion;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationManager;
import android.net.Uri;
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
import com.example.staucktion.api.RetrofitClient;
import com.example.staucktion.managers.LocationApiManager;
import com.example.staucktion.models.CategoryRequest;
import com.example.staucktion.models.CategoryResponse;
import com.example.staucktion.models.LocationCreateResponse;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
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
    private static final int REQ_LOCATION       = 101;
    private static final int REQ_CAMERA_PRIME   = 102;
    private static final int REQ_CAMERA_ON_OPEN = 103;
    private static final int REQ_CAMERA_CODE    = 200;

    private ApiService                      apiService;
    private LocationManager                 locationManager;
    private FusedLocationProviderClient     fusedLocationClient;
    private LocationApiManager              locationApiManager;

    private AutoCompleteTextView            categorySpinner;
    private MaterialTextView                noCategoryWarning;
    private MaterialButton                  createCategoryBtn;
    private TextView                        textAvatar;

    private double                          currentLatitude, currentLongitude;
    private int                             selectedCategoryId = -1;
    private List<CategoryResponse>          loadedCategories   = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Timber.plant(new Timber.DebugTree());

        apiService          = RetrofitClient.getInstance().create(ApiService.class);
        locationManager     = (LocationManager)getSystemService(LOCATION_SERVICE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationApiManager  = new LocationApiManager();

        setupToolbar();
        bindViews();
        MaterialAutoCompleteTextView spinner =
                findViewById(R.id.categoryAutoCompleteTextView);
        TextInputLayout layout =
                findViewById(R.id.categoryDropdownLayout);

        // 1) suppress the soft‐keyboard
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            spinner.setShowSoftInputOnFocus(false);
        }

        // 2) wire up arrow and field taps to open the list & focus
        layout.setStartIconOnClickListener(v -> {
            spinner.requestFocus();
            spinner.showDropDown();
        });
        spinner.setOnClickListener(v -> {
            spinner.requestFocus();
            spinner.showDropDown();
        });

        // 1) Request LOCATION
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
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

    // ① Create a Handler and Runnable at class level
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            // only if we still have LOCATION permission
            if (ContextCompat.checkSelfPermission(
                    MainActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {

                // pull the last known location…
                fusedLocationClient.getLastLocation()
                        .addOnSuccessListener(location -> {
                            if (location != null) {
                                currentLatitude  = location.getLatitude();
                                currentLongitude = location.getLongitude();
                                loadApprovedCategories();  // refresh the spinner
                            }
                        });
            }
            // schedule next run in 10 000 ms
            refreshHandler.postDelayed(this, 10_000);
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        // ② kick off the first refresh as soon as Activity is visible
        refreshHandler.post(refreshRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // ③ stop the loop when Activity is no longer in foreground
        refreshHandler.removeCallbacks(refreshRunnable);
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

        // ——— CREATE THEME BUTTON ———
        createCategoryBtn.setOnClickListener(v -> {
            resolveSelectedCategoryId();

            // no existing theme → check camera permission first
            if (selectedCategoryId < 0) {
                if (hasCameraPermission()) {
                    promptForCategoryNameAndCreateCategory();
                } else {
                    showCameraPermissionDialog();
                }
            } else {
                Toast.makeText(this,
                        "A theme is already selected. Clear it first if you want to create a new one.",
                        Toast.LENGTH_LONG).show();
            }
        });

        // ——— OPEN CAMERA BUTTON ———
        openCameraBtn.setOnClickListener(v -> {
            resolveSelectedCategoryId();

            if (selectedCategoryId < 0) {
                Toast.makeText(this,
                        "Please select or create a theme first.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(this,
                        "GPS must be enabled to take photos.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            if (hasCameraPermission()) {
                launchCamera();
            } else {
                // Only here do we ask for the CAMERA permission
                showCameraPermissionDialog();
            }
        });
    }

    /** After LOCATION granted, start location updates and prime the camera request */
    @SuppressLint("MissingPermission")
    private void onLocationPermissionGranted() {
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

        // prime Android’s camera permission dialog once at startup:
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                REQ_CAMERA_PRIME);
    }

    private void loadApprovedCategories() {
        apiService.getApprovedCategoriesByCoordinates(
                        currentLatitude, currentLongitude, "APPROVE")
                .enqueue(new Callback<List<CategoryResponse>>() {
                    @Override public void onResponse(
                            Call<List<CategoryResponse>> call,
                            Response<List<CategoryResponse>> resp) {
                        if (resp.isSuccessful() && resp.body()!=null) {
                            loadedCategories = resp.body();
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
                    @Override public void onFailure(
                            Call<List<CategoryResponse>> call, Throwable t) {
                        Toast.makeText(MainActivity.this,
                                "Failed to load themes",
                                Toast.LENGTH_SHORT).show();
                        noCategoryWarning.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void promptForCategoryNameAndCreateCategory() {
        View dialog = LayoutInflater.from(this)
                .inflate(R.layout.dialog_category, null);
        EditText box = dialog.findViewById(R.id.editCategoryName);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Enter theme name")
                .setView(dialog)
                .setPositiveButton("OK", (d,w)->{
                    String name = box.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this,
                                "Name cannot be empty",
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
                            String address = "Unknown";
                            try {
                                List<Address> addrs = new Geocoder(
                                        MainActivity.this, Locale.getDefault())
                                        .getFromLocation(
                                                currentLatitude, currentLongitude, 1);
                                if (!addrs.isEmpty())
                                    address = addrs.get(0).getAddressLine(0);
                            } catch (IOException ignored){}

                            createCategory(locId, name, address);
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

    private void createCategory(int locId, String name, String address) {
        apiService.createCategory(new CategoryRequest(
                        name, address, 5.0, locId, StatusEnum.WAIT))
                .enqueue(new Callback<CategoryResponse>() {
                    @Override public void onResponse(
                            Call<CategoryResponse> call,
                            Response<CategoryResponse> res) {
                        if (res.isSuccessful() && res.body()!=null) {
                            selectedCategoryId =
                                    Integer.parseInt(res.body().getId());
                            launchCamera();
                        } else {
                            Toast.makeText(MainActivity.this,
                                    "Theme creation failed",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override public void onFailure(
                            Call<CategoryResponse> call, Throwable t) {
                        Toast.makeText(MainActivity.this,
                                "Network error", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void launchCamera() {
        Intent i = new Intent(this, CameraActivity.class);
        i.putExtra("theme_id", selectedCategoryId);
        startActivityForResult(i, REQ_CAMERA_CODE);
    }


    /** If spinner text matches one of your loaded names, grab its ID here: */
    private void resolveSelectedCategoryId() {
        String txt = categorySpinner.getText().toString();
        for (CategoryResponse c : loadedCategories) {
            if (c.getName().equals(txt)) {
                selectedCategoryId = Integer.parseInt(c.getId());
                return;
            }
        }
        selectedCategoryId = -1;
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** your custom dialog from the screenshot */
    private void showCameraPermissionDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Camera Permission Required")
                .setMessage("Staucktion needs camera access to take and upload photos.\n\n" +
                        "Please enable CAMERA in settings.")
                .setPositiveButton("GO TO SETTINGS", (d,w)-> {
                    Intent i = new Intent(
                            android.provider.Settings
                                    .ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null));
                    startActivity(i);
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] perms, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, perms, grantResults);

        if (requestCode == REQ_LOCATION) {
            if (grantResults.length>0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onLocationPermissionGranted();
            } else {
                Toast.makeText(this,
                        "Location permission required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }

        if (requestCode == REQ_CAMERA_ON_OPEN) {
            if (grantResults.length>0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                Toast.makeText(this,
                        "Camera permission required", Toast.LENGTH_LONG).show();
            }
        }
        // REQ_CAMERA_PRIME is just to prime the system dialog
    }

    private void showLocationErrorDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Location unavailable")
                .setMessage("Could not get location. Retry?")
                .setPositiveButton("Retry", (d,w)-> onLocationPermissionGranted())
                .setNegativeButton("Exit",  (d,w)-> finish())
                .show();
    }

    // 1) loadUserProfile: always pull from "userFullName"
    private void loadUserProfile() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String role     = prefs.getString("userRole", "").toLowerCase(Locale.ROOT);
        String fullName = prefs.getString("userFullName", "?");
        String avatarText;

        // only show ADM/VAL if they are *not* a Google user
        if ("admin".equals(role)) {
            avatarText = "ADM";
        }
        else if ("validator".equals(role)) {
            avatarText = "VLD";
        }
        else {
            avatarText = getInitials(fullName);
        }

        textAvatar.setText(avatarText);
    }

    // 2) showAvatarPopup: only touch popupFullName / popupProfileImage
    private void showAvatarPopup(View anchor) {
        View popup = LayoutInflater.from(this)
                .inflate(R.layout.layout_avatar_popup, null);
        TextView popupName = popup.findViewById(R.id.popupFullName);
        ImageView popupPic = popup.findViewById(R.id.popupProfileImage);

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String userRole = prefs.getString("userRole", "");
        String fullName = prefs.getString("userFullName", "");
        boolean google  = isGoogleUser();


        if (!google
                && ("admin".equalsIgnoreCase(userRole)
                || "validator".equalsIgnoreCase(userRole))
        ) {
            String displayRole =
                    Character.toUpperCase(userRole.charAt(0)) +
                            userRole.substring(1).toLowerCase() +
                            " User";
            popupName.setText(displayRole);
        } else {
            popupName.setText(fullName);
        }

        String url = prefs.getString("userPhotoUrl", "");
        if (!url.isEmpty()) {
            Glide.with(this).load(url).into(popupPic);
        }

        new PopupWindow(popup,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                true)
                .showAsDropDown(anchor);
    }
    private String getInitials(String name){
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts){
            if (!p.isEmpty()) sb.append(p.charAt(0));
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }
    // helper to know if this session came via Google
    private boolean isGoogleUser(){
        GoogleSignInAccount acct = GoogleSignIn.getLastSignedInAccount(this);
        return acct != null;
    }

    private void performLogout() {
        SharedPreferences prefs =
                getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit().clear().apply();
        GoogleSignInClient client = GoogleSignIn.getClient(this,
                new com.google.android.gms.auth.api.signin
                        .GoogleSignInOptions.Builder(
                        com.google.android.gms.auth.api.signin
                                .GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail().build());
        client.signOut().addOnCompleteListener(t-> {
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        });
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAMERA_CODE
                && resultCode == RESULT_OK
                && data != null) {

            String path = data.getStringExtra("image_path");

            Intent upl = new Intent(this, ApiActivity.class);
            upl.putExtra("image_path", path);
            upl.putExtra("theme_id", selectedCategoryId);  // ← make sure this key matches!

            startActivity(upl);
            finish();
        }
    }
}
