package com.example.staucktion;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import timber.log.Timber;

public class CameraActivity extends Activity {
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private String currentPhotoPath;
    private static final String ACTION_KILL_CAMERA_ACTIVITY = "com.example.staucktion.KILL_CAMERA_ACTIVITY";
    private BroadcastReceiver killReceiver;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerKillReceiver();
        dispatchTakePictureIntent();
        Timber.i("Starting CameraActivity");
    }

    private void registerKillReceiver() {
        killReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Timber.i("Received broadcast: %s", intent.getAction());
                if (ACTION_KILL_CAMERA_ACTIVITY.equals(intent.getAction())) {
                    Timber.i("Killing CameraActivity");
                    finish();
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_KILL_CAMERA_ACTIVITY);
        registerReceiver(killReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (killReceiver != null) {
            unregisterReceiver(killReceiver);
        }
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "Error creating file", Toast.LENGTH_SHORT).show();
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.staucktion.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("image_path", currentPhotoPath);
            Timber.i("Image capture success, return file path: %s", currentPhotoPath);
            setResult(RESULT_OK, resultIntent);
        } else {
            Timber.i("Image capture failure, return cancel result");
            setResult(RESULT_CANCELED);
        }
        Timber.i("Finishing CameraActivity");
        finish();
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(null);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }
}
