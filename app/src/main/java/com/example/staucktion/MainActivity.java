package com.example.staucktion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView mtv;
    private Button btnChangeActivity;
    private LocationManager locationManager;
    private static final int CAMERA_REQUEST_CODE = 100;

    private final BroadcastReceiver gpsStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateGpsStatus(false); // Update GPS status on any change
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Link UI elements
        Button mbtn = findViewById(R.id.mbtn);
        btnChangeActivity = findViewById(R.id.btnChangeActivity);
        mtv = findViewById(R.id.mtv);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // click event on change activity button
        btnChangeActivity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ApiActivity.class);
                startActivity(intent);
            }
        });

        // Register BroadcastReceiver to listen for GPS status changes
        IntentFilter filter = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
        registerReceiver(gpsStatusReceiver, filter);

        // Initial GPS status check (no Toast)
        updateGpsStatus(false);

        // Set OnClickListener for the button
        mbtn.setOnClickListener(v -> {
            if (isGpsEnabled()) {
                // Open the camera
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                PackageManager packageManager = getPackageManager();
                if (cameraIntent.resolveActivity(packageManager) != null) {
                    startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
                }
            } else {
                // Show a warning message
                Toast.makeText(MainActivity.this, "Please turn on location services to be able to take a photo.", Toast.LENGTH_LONG).show();
            }
        });
    }

    // Method to check if GPS is enabled
    private boolean isGpsEnabled() {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    // Method to update GPS status
    private void updateGpsStatus(boolean showToast) {
        if (isGpsEnabled()) {
            mtv.setText(R.string.gps_is_on);
        } else {
            mtv.setText(R.string.gps_is_off);
            if (showToast) {
                Toast.makeText(MainActivity.this, "GPS is off. Please turn it back on.", Toast.LENGTH_LONG).show();
            }
        }
    }

    // Handle the result of the camera activity
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAMERA_REQUEST_CODE) {
            // Check if the GPS is enabled after the camera activity finishes
            if (!isGpsEnabled()) {
                // Show a warning if GPS is off
                Toast.makeText(MainActivity.this, "GPS is off. Please turn it back on to proceed.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the receiver when the activity is paused
        unregisterReceiver(gpsStatusReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register the receiver again when the activity is resumed
        registerReceiver(gpsStatusReceiver, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));

        // Check GPS status without showing the Toast message initially
        updateGpsStatus(false);
    }
}