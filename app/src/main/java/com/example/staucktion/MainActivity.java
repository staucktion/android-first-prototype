// src/main/java/com/example/staucktion/MainActivity.java
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
import android.util.Log;
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
import com.example.staucktion.models.UserInfoResponse;
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
    private static final int CAMERA_REQ_CODE   = 100;
    private static final int LOC_PERM_REQ_CODE = 101;
    private static final int CAM_PERM_REQ_CODE = 102;

    // services & UI
    private ApiService                   apiService;
    private LocationManager              locationManager;
    private FusedLocationProviderClient  fusedLocationClient;
    private LocationApiManager           locationApiManager;
    private AutoCompleteTextView         categorySpinner;
    private MaterialTextView             noCategoryWarning;
    private MaterialButton               createCategoryBtn;
    private TextView                     textAvatar;

    // state
    private double                       currentLatitude, currentLongitude;
    private int                          selectedCategoryId = -1;
    private List<CategoryResponse> loadedCategories  = new ArrayList<>();

    // for refreshing categories
    private final Handler categoryHandler = new Handler(Looper.getMainLooper());
    private final Runnable categoryRefreshRunnable = new Runnable() {
        @Override public void run() {
            loadApprovedCategories(currentLatitude, currentLongitude);
            categoryHandler.postDelayed(this, 10_000);
        }
    };

    // listen for GPS toggles while in camera
    private final BroadcastReceiver gpsToggleReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                sendBroadcast(new Intent("com.example.staucktion.KILL_CAMERA_ACTIVITY"));
            }
        }
    };
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Timber.plant(new Timber.DebugTree());

        apiService = RetrofitClient.getInstance().create(ApiService.class);

        // 1) Local expiry check
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String token = prefs.getString("appToken", null);
        long   expiry = prefs.getLong("appTokenExpiry", 0);
        long now    = System.currentTimeMillis();

        Log.d(TAG, "onCreate: token=" + token +
                "  expiry=" + expiry +
                "  now="    + now +
                "  expired?=" + (now > expiry));
        if (token == null || now > expiry) {
            Log.d(TAG, "→ token invalid or expired, redirecting to login");

            // no valid token → go to login
            prefs.edit().clear().apply();
            redirectToLogin();
            return;
        }

        // 2) Tell Retrofit about it
        RetrofitClient.getInstance().setAuthToken(token);

        // 3) Verify on server before showing the UI
        apiService.getUserInfo().enqueue(new Callback<UserInfoResponse>() {
            @Override public void onResponse(
                    @NonNull Call<UserInfoResponse> call,
                    @NonNull Response<UserInfoResponse> resp) {
                if (resp.isSuccessful()
                        && resp.body() != null
                        && resp.body().getUser() != null) {
                    // ✅ token is valid on server → initialize UI
                    runOnUiThread(() -> initAfterAuth());
                } else {
                    // ❌ invalid on server → clear & redirect
                    prefs.edit().clear().apply();
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this,
                                "Session expired, please log in again.",
                                Toast.LENGTH_LONG).show();
                        redirectToLogin();
                    });
                }
            }
            @Override public void onFailure(
                    @NonNull Call<UserInfoResponse> call,
                    @NonNull Throwable t) {
                // network error verifying session → still let user in
                Timber.e(t, "Could not verify session");
                runOnUiThread(() -> initAfterAuth());
            }
        });
    }

    private void redirectToLogin() {
        startActivity(new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    @SuppressLint("MissingPermission")
    private void initAfterAuth() {
        // —— Toolbar & Avatar Popup ——
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        textAvatar = toolbar.findViewById(R.id.textAvatar);
        textAvatar.setOnClickListener(this::showAvatarPopup);
        toolbar.findViewById(R.id.logoutIcon)
                .setOnClickListener(v -> performLogout());
        loadUserProfile();

        // —— Location & Services ——
        locationManager     = (LocationManager) getSystemService(LOCATION_SERVICE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationApiManager  = new LocationApiManager();

        // —— Wire up UI ——
        categorySpinner   = findViewById(R.id.categoryAutoCompleteTextView);
        noCategoryWarning = findViewById(R.id.noCategoryWarning);
        createCategoryBtn = findViewById(R.id.createCategoryButton);
        Button openCameraBtn = findViewById(R.id.openCamerabtn);

        createCategoryBtn.setOnClickListener(v ->
                promptForCategoryNameAndCreateCategory(currentLatitude, currentLongitude));
        openCameraBtn.setOnClickListener(v -> handleOpenCamera());

        // —— Location Permission & Initial Load ——
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOC_PERM_REQ_CODE);
        } else {
            getLastLocation();
        }

        // —— Periodic Refresh ——
        categoryHandler.postDelayed(categoryRefreshRunnable, 10_000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(gpsToggleReceiver,
                new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(gpsToggleReceiver);
        categoryHandler.removeCallbacks(categoryRefreshRunnable);
    }

    private void handleOpenCamera() {
        if (selectedCategoryId < 0) {
            Toast.makeText(this,
                    "Please select or create a category first.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this,
                    "GPS must be enabled to take photos.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAM_PERM_REQ_CODE);
        } else {
            checkCategoryAndLaunchCamera();
        }
    }

    @SuppressLint("MissingPermission")
    private void getLastLocation() {
        LocationRequest req = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5_000)
                .setFastestInterval(2_000);

        fusedLocationClient.requestLocationUpdates(req,
                new LocationCallback() {
                    @Override public void onLocationResult(@NonNull LocationResult result) {
                        fusedLocationClient.removeLocationUpdates(this);
                        if (result.getLastLocation() != null) {
                            currentLatitude  = result.getLastLocation().getLatitude();
                            currentLongitude = result.getLastLocation().getLongitude();
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
                    @Override public void onResponse(
                            @NonNull Call<List<CategoryResponse>> call,
                            @NonNull Response<List<CategoryResponse>> res) {
                        if (res.isSuccessful() && res.body() != null) {
                            loadedCategories = res.body();
                            updateSpinner(loadedCategories);
                            noCategoryWarning.setVisibility(
                                    loadedCategories.isEmpty() ? View.VISIBLE : View.GONE);
                        } else {
                            noCategoryWarning.setVisibility(View.VISIBLE);
                        }
                    }
                    @Override public void onFailure(
                            @NonNull Call<List<CategoryResponse>> call,
                            @NonNull Throwable t) {
                        Timber.e(t, "Error loading categories");
                        Toast.makeText(MainActivity.this,
                                "Failed to load categories", Toast.LENGTH_SHORT).show();
                        noCategoryWarning.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void updateSpinner(List<CategoryResponse> cats) {
        List<String> names = new ArrayList<>();
        for (CategoryResponse c : cats) names.add(c.getName());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, names);
        categorySpinner.setAdapter(adapter);
        categorySpinner.setOnItemClickListener((p,v,pos,id) -> {
            try {
                selectedCategoryId = Integer.parseInt(cats.get(pos).getId());
                noCategoryWarning.setVisibility(View.GONE);
            } catch (NumberFormatException ex) {
                Timber.e(ex, "Invalid category id");
            }
        });
    }

    private void checkCategoryAndLaunchCamera() {
        if (loadedCategories.isEmpty()) {
            promptForCategoryNameAndCreateCategory(
                    currentLatitude, currentLongitude);
        } else {
            selectedCategoryId =
                    Integer.parseInt(loadedCategories.get(0).getId());
            launchCamera();
        }
    }

    private void promptForCategoryNameAndCreateCategory(
            double lat, double lng) {
        View dialog = LayoutInflater.from(this)
                .inflate(R.layout.dialog_category, null);
        EditText input = dialog.findViewById(R.id.editCategoryName);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Enter Category Name")
                .setView(dialog)
                .setPositiveButton("OK", (d,w)->{
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this,
                                "Name cannot be empty", Toast.LENGTH_SHORT).show();
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
                    @Override public void onResponse(
                            @NonNull Call<LocationCreateResponse> call,
                            @NonNull Response<LocationCreateResponse> res) {
                        if (res.isSuccessful()
                                && res.body()!=null
                                && res.body().getLocation()!=null) {
                            int locId = Integer.parseInt(
                                    res.body().getLocation().getId());
                            getAddressFromCoordinates(lat, lng, address->
                                    createCategory(locId, name, address));
                        } else {
                            Toast.makeText(MainActivity.this,
                                    "Location creation failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override public void onFailure(
                            @NonNull Call<LocationCreateResponse> call,
                            @NonNull Throwable t) {
                        Timber.e(t,"Failed to create location");
                        Toast.makeText(MainActivity.this,
                                "Network error", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void createCategory(int locationId,
                                String name, String address) {
        CategoryRequest req = new CategoryRequest(
                name, address, 5.0, locationId, StatusEnum.WAIT);
        apiService.createCategory(req)
                .enqueue(new Callback<CategoryResponse>() {
                    @Override public void onResponse(
                            @NonNull Call<CategoryResponse> call,
                            @NonNull Response<CategoryResponse> res) {
                        if (res.isSuccessful() && res.body()!=null) {
                            selectedCategoryId =
                                    Integer.parseInt(res.body().getId());
                            launchCamera();
                        } else {
                            Toast.makeText(MainActivity.this,
                                    "Category creation failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override public void onFailure(
                            @NonNull Call<CategoryResponse> call,
                            @NonNull Throwable t) {
                        Timber.e(t,"Failed to create category");
                        Toast.makeText(MainActivity.this,
                                "Network error", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void getAddressFromCoordinates(
            double lat, double lng,
            OnAddressObtainedListener listener) {
        try {
            List<Address> list = new Geocoder(this, Locale.getDefault())
                    .getFromLocation(lat, lng, 1);
            if (list != null && !list.isEmpty()) {
                listener.onAddressObtained(list.get(0).getAddressLine(0));
            } else {
                listener.onAddressObtained("Unknown Address");
            }
        } catch (IOException ex) {
            Timber.e(ex,"Reverse geocoding failed");
            listener.onAddressObtained("Unknown Address");
        }
    }

    private void launchCamera() {
        Intent i = new Intent(this, CameraActivity.class);
        i.putExtra("category_id", selectedCategoryId);
        startActivityForResult(i, CAMERA_REQ_CODE);
    }

    @Override public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] perms, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, perms, grantResults);
        if (requestCode == LOC_PERM_REQ_CODE) {
            if (grantResults.length>0
                    && grantResults[0]==PackageManager.PERMISSION_GRANTED) {
                getLastLocation();
            } else {
                Toast.makeText(this,
                        "Location permission required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
        if (requestCode == CAM_PERM_REQ_CODE) {
            if (grantResults.length>0
                    && grantResults[0]==PackageManager.PERMISSION_GRANTED) {
                checkCategoryAndLaunchCamera();
            } else {
                Toast.makeText(this,
                        "Camera permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showLocationErrorDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Location unavailable")
                .setMessage("Could not get location – retry?")
                .setPositiveButton("Retry", (d,w)->getLastLocation())
                .setNegativeButton("Exit",   (d,w)->finish())
                .show();
    }

    private void showAvatarPopup(View anchor) {
        View popup = LayoutInflater.from(this)
                .inflate(R.layout.layout_avatar_popup, null);
        TextView name = popup.findViewById(R.id.popupFullName);
        ImageView pic = popup.findViewById(R.id.popupProfileImage);
        SharedPreferences prefs =
                getSharedPreferences("AppPrefs", MODE_PRIVATE);
        name.setText(prefs.getString("userName","User"));
        String url = prefs.getString("userPhotoUrl","");
        if (!url.isEmpty()) Glide.with(this).load(url).into(pic);
        new PopupWindow(popup,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, true)
                .showAsDropDown(anchor);
    }

    private void loadUserProfile() {
        SharedPreferences prefs =
                getSharedPreferences("AppPrefs", MODE_PRIVATE);
        textAvatar.setText(
                getAbbreviation(prefs.getString("userName","?")));
    }

    private String getAbbreviation(String fullName) {
        if (fullName==null||fullName.isEmpty()) return "?";
        StringBuilder ab = new StringBuilder();
        for (String part: fullName.trim().split("\\s+")) {
            ab.append(part.charAt(0));
        }
        return ab.toString().toUpperCase();
    }

    private void performLogout() {
        SharedPreferences prefs =
                getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit().clear().apply();
        GoogleSignInClient client =
                GoogleSignIn.getClient(this,
                        new com.google.android.gms.auth.api.signin.GoogleSignInOptions
                                .Builder(com.google.android.gms.auth.api.signin
                                .GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestEmail().build()
                );
        client.signOut().addOnCompleteListener(t->redirectToLogin());
    }

    @Override protected void onActivityResult(
            int req, int res, Intent data) {
        super.onActivityResult(req,res,data);
        if (req==CAMERA_REQ_CODE&&res==RESULT_OK&&data!=null){
            String path = data.getStringExtra("image_path");
            Intent upl  = new Intent(this, ApiActivity.class);
            upl.putExtra("image_path",path);
            upl.putExtra("category_id",selectedCategoryId);
            startActivity(upl);
        }
    }
}
