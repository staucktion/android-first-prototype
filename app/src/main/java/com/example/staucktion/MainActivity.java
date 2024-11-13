package com.example.staucktion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView mtv;
    private LocationManager locationManager;

    private final BroadcastReceiver gpsStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isGpsEnabled()) {
                mtv.setText(R.string.gps_is_on);
            } else {
                mtv.setText(R.string.gps_is_off);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Link the variables to the UI elements
        Button mbtn = findViewById(R.id.mbtn);
        mtv = findViewById(R.id.mtv);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Register the BroadcastReceiver to listen for GPS status changes
        IntentFilter filter = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
        registerReceiver(gpsStatusReceiver, filter);

        // Initial check
        if (isGpsEnabled()) {
            mtv.setText(R.string.gps_is_on);
        } else {
            mtv.setText(R.string.gps_is_off);
        }

        // Set the OnClickListener for the button
        mbtn.setOnClickListener(v -> {
            if (isGpsEnabled()) {
                // Open the camera
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                PackageManager packageManager = getPackageManager();
                if (cameraIntent.resolveActivity(packageManager) != null) {
                    startActivity(cameraIntent);
                }
            } else {
                // Show a warning message
                Toast.makeText(MainActivity.this, "Please turn on your location services to be able to take a photograph.", Toast.LENGTH_LONG).show();
            }
        });
    }

    // Method to check if GPS is enabled
    private boolean isGpsEnabled() {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
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
    }
}
