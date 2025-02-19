package com.example.staucktion;

import static com.example.staucktion.R.layout.activity_main;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
    private final BroadcastReceiver gpsStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateGpsStatus(false); // Update GPS status on any change
        }
    };

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(activity_main);
        instance = this;
        Timber.plant(new Timber.DebugTree());
        Timber.i("Starting MainActivity");
        Intent intent = new Intent(MainActivity.this, ApiActivity.class);
        startActivity(intent);

        // Link UI elements
        Button openCamerabtn = findViewById(R.id.openCamerabtn);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Register BroadcastReceiver to listen for GPS status changes
        IntentFilter filter = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
        registerReceiver(gpsStatusReceiver, filter);

        // Initial GPS status check (no Toast)
        updateGpsStatus(false);

        // Set OnClickListener for the button
        openCamerabtn.setOnClickListener(v -> {
            Log.d("MainActivity", "GPS enabled: " + isGpsEnabled());

            if (isGpsEnabled()) {
                // Open the camera
                Intent cameraIntent = new Intent(this, CameraActivity.class);
                // Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE); // native one
                PackageManager packageManager = getPackageManager();
                if (cameraIntent.resolveActivity(packageManager) != null) {
                    startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
                    isCameraActive = true;
                    hasGPSTurnedOffOnceWhileInCamera = false;
                }
            } else {
                // Show a warning message
                Toast.makeText(MainActivity.this, "Please turn on location services to be able to take a photo.", Toast.LENGTH_LONG).show();
            }
        });
    }

    public static MainActivity getInstance() {
        return instance;
    }

    // Method to check if GPS is enabled
    private boolean isGpsEnabled() {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    // Method to update GPS status
    // Method to update GPS status without modifying the UI
    private void updateGpsStatus(boolean showToast) {
        if (!isGpsEnabled()) {
            if (showToast) {
                Toast.makeText(MainActivity.this, "GPS is off. Please turn it back on.", Toast.LENGTH_LONG).show();
            }
            if (isCameraActive && !hasGPSTurnedOffOnceWhileInCamera) {
                hasGPSTurnedOffOnceWhileInCamera = true;
                Timber.i("GPS is off after camera is opened");
                Intent killIntent = new Intent("com.example.staucktion.KILL_CAMERA_ACTIVITY");
                sendBroadcast(killIntent);
            }
        }
    }

    // Handle the result of the camera activity
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (this.isCameraActivityFinishedProperly(requestCode, resultCode, data)) {
            // Show a success message if the picture was taken successfully
            String imagePath = data.getStringExtra("image_path");
            Toast.makeText(this, "Image saved at: " + imagePath, Toast.LENGTH_LONG).show();
        }
    }

    public void setIsCameraActive(boolean isCameraActive) {
        this.isCameraActive = isCameraActive;
    }

    public boolean getIsGpsEnabled() {
        return isGpsEnabled();
    }

    public void setHasGPSTurnedOffOnceWhileInCamera(boolean hasGPSTurnedOffOnceWhileInCamera) {
        this.hasGPSTurnedOffOnceWhileInCamera = hasGPSTurnedOffOnceWhileInCamera;
    }

    public boolean isCameraActivityFinishedProperly(int requestCode, int resultCode, Intent data) {
        boolean result = false;
        if (requestCode == CAMERA_REQUEST_CODE) {
            // Check if the GPS has been disabled at least once while taking picture after the camera activity finishes
            if (hasGPSTurnedOffOnceWhileInCamera || !isGpsEnabled()) {
                // Show a warning if GPS is off
                Toast.makeText(MainActivity.this, "GPS was off while taking the picture. Please turn it back on.", Toast.LENGTH_LONG).show();
            } else if(resultCode == RESULT_OK) {
                // Handling of the result changes based on from which activity it has been called from
                result = true;
            }
            isCameraActive = false;
            hasGPSTurnedOffOnceWhileInCamera = false;
        }
        return result;
    }

}