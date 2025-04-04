package com.example.staucktion;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textview.MaterialTextView;

public class UploadActivity extends AppCompatActivity {

    private ShapeableImageView ivUploadPreview;
    private MaterialTextView tvUploadStatus;
    private MaterialButton btnSelectPhoto, btnUpload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        ivUploadPreview = findViewById(R.id.ivUploadPreview);
        tvUploadStatus = findViewById(R.id.tvUploadStatus);
        btnSelectPhoto = findViewById(R.id.btnSelectPhoto);
        btnUpload = findViewById(R.id.btnUpload);

        // Set click listeners
        btnSelectPhoto.setOnClickListener(v -> {
            // TODO: Open camera or gallery to select a photo
        });

        btnUpload.setOnClickListener(v -> {
            // TODO: Upload the photo to your server or handle upload logic
        });
    }
}
