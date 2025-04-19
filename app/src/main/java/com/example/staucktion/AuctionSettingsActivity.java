package com.example.staucktion;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.staucktion.api.ApiService;
import com.example.staucktion.api.RetrofitClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

public class AuctionSettingsActivity extends AppCompatActivity {

    private TextInputEditText editPurchaseNowPrice;
    private MaterialCheckBox  checkAuctionable;
    private MaterialButton    btnSaveAuctionSettings;
    private ShapeableImageView photoPreview;
    private MaterialTextView  photoStatusText;

    private int    photoId;
    private String photoUrl;
    private String status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auction_settings);

        // 1) Find your views
        editPurchaseNowPrice    = findViewById(R.id.editPurchaseNowPrice);
        checkAuctionable        = findViewById(R.id.checkAuctionable);
        btnSaveAuctionSettings  = findViewById(R.id.btnSaveAuctionSettings);
        photoPreview            = findViewById(R.id.photoPreview);
        photoStatusText         = findViewById(R.id.photoStatusText);

        // 2) Extract Intent extras
        Intent intent = getIntent();
        photoId  = intent.getIntExtra("photo_id", -1);
        status   = intent.getStringExtra("status");
        photoUrl = intent.getStringExtra("photo_url");

        // 3) Update status text
        if (status != null) {
            photoStatusText.setText("Your photo was " + status + "!");
        }

        // 4) Load or placeholder
        if (photoUrl != null && !photoUrl.isEmpty()) {
            Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.placeholder_image)  // your placeholder
                    .error(R.drawable.error_image)             // your error fallback
                    .into(photoPreview);
        } else {
            photoPreview.setImageResource(R.drawable.placeholder_image);
        }

        // 5) Toggle price field when auctionable is checked
        checkAuctionable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editPurchaseNowPrice.setEnabled(!isChecked);
            if (isChecked) {
                editPurchaseNowPrice.setText("");
            }
        });

        // 6) Save button
        btnSaveAuctionSettings.setOnClickListener(v -> saveAuctionSettings());
        Timber.d("🔍 AuctionSettingsActivity received photo_id=%d, photo_url=%s, status=%s",
                photoId, photoUrl, status);

    }

    private void saveAuctionSettings() {
        boolean isAuctionable = checkAuctionable.isChecked();
        String priceText      = editPurchaseNowPrice.getText().toString().trim();

        if (!isAuctionable && priceText.isEmpty()) {
            Toast.makeText(this, "Please set a price or mark as auctionable.", Toast.LENGTH_SHORT).show();
            return;
        }

        double price = 0.0;
        if (!isAuctionable) {
            try {
                price = Double.parseDouble(priceText);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid price format.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        ApiService apiService = RetrofitClient.getInstance().create(ApiService.class);
        apiService.saveAuctionSettings(photoId, price, isAuctionable)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call,
                                           @NonNull Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(AuctionSettingsActivity.this,
                                    "Auction settings saved successfully!",
                                    Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            Toast.makeText(AuctionSettingsActivity.this,
                                    "Failed to save settings.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call,
                                          @NonNull Throwable t) {
                        Toast.makeText(AuctionSettingsActivity.this,
                                "Error: " + t.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}