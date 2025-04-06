package com.example.staucktion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import timber.log.Timber;

public class CameraActivity extends AppCompatActivity {

    private static final String FILE_PROVIDER_AUTHORITY = "com.example.staucktion.fileprovider";
    private static final String ACTION_KILL_CAMERA_ACTIVITY = "com.example.staucktion.KILL_CAMERA_ACTIVITY";

    private String currentPhotoPath;
    private BroadcastReceiver killReceiver;
    private BroadcastReceiver gpsStateReceiver;
    private ActivityResultLauncher<Uri> takePictureLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Register a receiver to listen for external kill commands
        registerKillReceiver();
        // Register a receiver to listen for GPS state changes
        registerGpsStateReceiver();

        // Initialize the ActivityResultLauncher with the TakePicture contract.
        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                result -> {
                    if (result) {
                        Timber.i("Image capture successful, file path: %s", currentPhotoPath);
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("image_path", currentPhotoPath);
                        setResult(RESULT_OK, resultIntent);
                    } else {
                        Timber.i("Image capture canceled or failed.");
                        setResult(RESULT_CANCELED);
                    }
                    Timber.i("Finishing CameraActivity");
                    finish();
                }
        );

        // Start the process of capturing an image.
        dispatchTakePictureIntent();
        Timber.i("Starting CameraActivity");
    }

    /**
     * Registers a BroadcastReceiver to listen for external "KILL_CAMERA_ACTIVITY" actions.
     */
    private void registerKillReceiver() {
        killReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Timber.i("Received broadcast: %s", intent.getAction());
                if (ACTION_KILL_CAMERA_ACTIVITY.equals(intent.getAction())) {
                    Timber.i("Killing CameraActivity as requested.");
                    CameraActivity.this.setResult(RESULT_CANCELED);
                    finish();
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_KILL_CAMERA_ACTIVITY);
        registerReceiver(killReceiver, filter);
    }

    /**
     * Registers a BroadcastReceiver to listen for GPS state changes.
     */
    private void registerGpsStateReceiver() {
        gpsStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (LocationManager.PROVIDERS_CHANGED_ACTION.equals(intent.getAction())) {
                    LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                    boolean gpsEnabled = locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                    if (!gpsEnabled) {
                        Toast.makeText(CameraActivity.this,
                                "GPS has been turned off during photo capture. This app requires GPS to ensure accurate location data. " +
                                        "The photo capture process is canceled. Please enable GPS and try again.",
                                Toast.LENGTH_LONG).show();
                        // Set an extra flag to indicate GPS cancellation.
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("gps_disabled", true);
                        CameraActivity.this.setResult(RESULT_CANCELED, resultIntent);
                        finish();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
        registerReceiver(gpsStateReceiver, filter);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (killReceiver != null) {
            unregisterReceiver(killReceiver);
        }
        if (gpsStateReceiver != null) {
            unregisterReceiver(gpsStateReceiver);
        }
    }

    /**
     * Creates the file to store the captured image and launches the camera.
     */
    private void dispatchTakePictureIntent() {
        File photoFile;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            Timber.e(ex, "Error creating image file");
            Toast.makeText(this, "Error creating file", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        // Create a content URI using FileProvider.
        Uri photoURI = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, photoFile);
        takePictureLauncher.launch(photoURI);
    }

    /**
     * Creates an image file with a unique timestamp-based name in the external pictures directory.
     *
     * @return The created image file.
     * @throws IOException If file creation fails.
     */
    private File createImageFile() throws IOException {
        // Create an image file name with a timestamp.
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }
}
