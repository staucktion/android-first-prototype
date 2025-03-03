package com.example.staucktion;

import static com.example.staucktion.R.layout.activity_main;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import timber.log.Timber;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static MainActivity instance;
    private LocationManager locationManager;
    private static final int CAMERA_REQUEST_CODE = 100;
    private boolean isCameraActive = false;
    private boolean hasGPSTurnedOffOnceWhileInCamera = false;

    private final android.content.BroadcastReceiver gpsStatusReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            updateGpsStatus();
        }
    };

    @SuppressLint({"MissingInflatedId", "LogNotTimber"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if user is logged in by verifying if an app token exists.
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String appToken = prefs.getString("appToken", null);
        if (appToken == null) {
            // Not logged in; redirect to LoginActivity.
            Intent loginIntent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(loginIntent);
            finish();
            return;
        }

        // Set the content view after login verification.
        setContentView(activity_main);
        instance = this;
        Timber.plant(new Timber.DebugTree());
        Timber.i("Starting MainActivity");

        // Link UI elements
        Button openCamerabtn = findViewById(R.id.openCamerabtn);
        Button profilebtn = findViewById(R.id.profilebtn);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Register BroadcastReceiver to listen for GPS changes.
        registerReceiver(gpsStatusReceiver, new android.content.IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
        updateGpsStatus();

        // Set OnClickListener for the "Open Camera" button.
        openCamerabtn.setOnClickListener(v -> {
            Log.d("MainActivity", "GPS enabled: " + isGpsEnabled());
            if (isGpsEnabled()) {
                // Open CameraActivity.
                Intent cameraIntent = new Intent(this, CameraActivity.class);
                PackageManager packageManager = getPackageManager();
                if (cameraIntent.resolveActivity(packageManager) != null) {
                    startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
                    isCameraActive = true;
                    hasGPSTurnedOffOnceWhileInCamera = false;
                }
            } else {
                Toast.makeText(MainActivity.this, "Please turn on location services to take a photo.", Toast.LENGTH_LONG).show();
            }
        });

        // Set OnClickListener for the "Profile" button.
        profilebtn.setOnClickListener(v -> {
            // Open ProfileActivity.
            Intent profileIntent = new Intent(MainActivity.this, ProfileActivity.class);
            startActivity(profileIntent);
        });
    }

    public static MainActivity getInstance() {
        return instance;
    }

    // Helper method to check if GPS is enabled.
    private boolean isGpsEnabled() {
        return locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    // Update GPS status (send broadcast if needed).
    private void updateGpsStatus() {
        if (!isGpsEnabled()) {
            if (isCameraActive && !hasGPSTurnedOffOnceWhileInCamera) {
                hasGPSTurnedOffOnceWhileInCamera = true;
                Timber.i("GPS is off after camera is opened");
                Intent killIntent = new Intent("com.example.staucktion.KILL_CAMERA_ACTIVITY");
                sendBroadcast(killIntent);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_REQUEST_CODE) {
            // Check for null data before processing
            if (data != null && isCameraActivityFinishedProperly(requestCode, resultCode)) {
                String imagePath = data.getStringExtra("image_path");
                Toast.makeText(this, "Image saved at: " + imagePath, Toast.LENGTH_LONG).show();
                // Optionally, trigger photo upload here.
            } else {
                Toast.makeText(this, "Camera activity did not finish properly or no image data was returned.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Setter for isCameraActive.
    public void setIsCameraActive(boolean isCameraActive) {
        this.isCameraActive = isCameraActive;
    }

    // Getter for GPS enabled state.
    public boolean getIsGpsEnabled() {
        return isGpsEnabled();
    }

    // Setter for hasGPSTurnedOffOnceWhileInCamera.
    public void setHasGPSTurnedOffOnceWhileInCamera(boolean hasGPSTurnedOffOnceWhileInCamera) {
        this.hasGPSTurnedOffOnceWhileInCamera = hasGPSTurnedOffOnceWhileInCamera;
    }

    // Checks if the camera activity finished properly based on the result code.
    public boolean isCameraActivityFinishedProperly(int requestCode, int resultCode) {
        boolean result = false;
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (hasGPSTurnedOffOnceWhileInCamera || !isGpsEnabled()) {
                Toast.makeText(MainActivity.this, "GPS was off while taking the picture. Please turn it back on.", Toast.LENGTH_LONG).show();
            } else if (resultCode == RESULT_OK) {
                result = true;
            }
            // Reset flags.
            isCameraActive = false;
            hasGPSTurnedOffOnceWhileInCamera = false;
        }
        return result;
    }
}
