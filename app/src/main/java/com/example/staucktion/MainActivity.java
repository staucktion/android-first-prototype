package com.example.staucktion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Button mbtn;
    private TextView mtv;
    private LocationManager locationManager;

    private final BroadcastReceiver gpsStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isGpsEnabled()) {
                mtv.setText("GPS is ON");
            } else {
                mtv.setText("GPS is OFF");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Link the variables to the UI elements
        mbtn = findViewById(R.id.mbtn);
        mtv = findViewById(R.id.mtv);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Register the BroadcastReceiver to listen for GPS status changes
        IntentFilter filter = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
        registerReceiver(gpsStatusReceiver, filter);

        // Initial check
        if (isGpsEnabled()) {
            mtv.setText("GPS is ON");
        } else {
            mtv.setText("GPS is OFF");
        }
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
