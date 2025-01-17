package com.example.staucktion.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.widget.Toast;

import timber.log.Timber;

public class GPSUtils {

    private LocationManager locationManager;
    private boolean isCameraActive = false;
    private boolean hasGPSTurnedOffOnceWhileInCamera = false;
    private BroadcastReceiver gpsStatusReceiver;

    public GPSUtils(Context context) {
        // Initialize location manager
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        // Initialize and register the GPS status receiver
        gpsStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateGpsStatus(context, false); // Update GPS status on any change
            }
        };
        IntentFilter filter = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
        context.registerReceiver(gpsStatusReceiver, filter);
    }

    // Check if GPS is enabled
    public boolean isGpsEnabled() {
        return locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    // Method to update GPS status
    public void updateGpsStatus(Context context, boolean showToast) {
        if (isGpsEnabled()) {
            Timber.i("GPS is on");
        } else {
            Timber.i("GPS is off");
            if (showToast) {
                Toast.makeText(context, "GPS is off. Please turn it back on.", Toast.LENGTH_LONG).show();
            }
            if (isCameraActive && !hasGPSTurnedOffOnceWhileInCamera) {
                hasGPSTurnedOffOnceWhileInCamera = true;
                Timber.i("GPS is off after camera is opened");
                Intent killIntent = new Intent("com.example.staucktion.KILL_CAMERA_ACTIVITY");
                context.sendBroadcast(killIntent);
            }
        }
    }

    // Method to check if the camera activity finished properly
    public boolean isCameraActivityFinishedProperly(int requestCode, int resultCode, Intent data, int cameraRequestCode) {
        boolean result = false;
        if (requestCode == cameraRequestCode) {
            if (hasGPSTurnedOffOnceWhileInCamera || !isGpsEnabled()) {
                // Warning if GPS is off
                Timber.i("GPS was off while taking the picture.");
            } else if (resultCode == android.app.Activity.RESULT_OK) {
                result = true;
            }
            isCameraActive = false;
            hasGPSTurnedOffOnceWhileInCamera = false;
        }
        return result;
    }

    // Getter and Setter for isCameraActive
    public void setIsCameraActive(boolean isCameraActive) {
        this.isCameraActive = isCameraActive;
    }

    // Getter and Setter for hasGPSTurnedOffOnceWhileInCamera
    public void setHasGPSTurnedOffOnceWhileInCamera(boolean hasGPSTurnedOffOnceWhileInCamera) {
        this.hasGPSTurnedOffOnceWhileInCamera = hasGPSTurnedOffOnceWhileInCamera;
    }

    public boolean getIsGpsEnabled() {
        return isGpsEnabled();
    }

    // Clean up resources (to be called in onDestroy or similar lifecycle method)
    public void unregisterReceiver(Context context) {
        if (gpsStatusReceiver != null) {
            context.unregisterReceiver(gpsStatusReceiver);
        }
    }
}