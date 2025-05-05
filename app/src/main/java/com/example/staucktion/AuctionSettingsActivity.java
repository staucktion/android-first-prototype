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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

public class AuctionSettingsActivity extends AppCompatActivity {
    private TextInputEditText  editPurchaseNowPrice;
    private MaterialCheckBox   checkAuctionable;
    private MaterialButton     btnSaveAuctionSettings;
    private ShapeableImageView photoPreview;
    private MaterialTextView   photoStatusText;

    private int    photoId;
    private String photoUrl;
    private String action;
    private static final BigDecimal MAX_PRICE = new BigDecimal("1000000.00");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auction_settings);

        // Bind views
        editPurchaseNowPrice   = findViewById(R.id.editPurchaseNowPrice);
        checkAuctionable       = findViewById(R.id.checkAuctionable);
        btnSaveAuctionSettings = findViewById(R.id.btnSaveAuctionSettings);
        photoPreview           = findViewById(R.id.photoPreview);
        photoStatusText        = findViewById(R.id.photoStatusText);

        // Extract Intent extras
        Intent intent = getIntent();
        photoId   = intent.getIntExtra("notification_photo_id", -1);
        action    = intent.getStringExtra("notification_action");
        photoUrl  = intent.getStringExtra("notification_photo_url");
        String fullMsg = intent.getStringExtra("notification_message");

        Timber.d("AuctionSettingsActivity → photoId=%d, action=%s, photoUrl=%s, fullMsg=%s",
                photoId, action, photoUrl, fullMsg);

        // Only proceed if approved
        if (!"approve".equalsIgnoreCase(action)) {
            Toast.makeText(this,
                            "Photo is not approved, returning.", Toast.LENGTH_SHORT)
                    .show();
            finish();
            return;
        }

        // Display full notification message if available, else default
        if (fullMsg != null && !fullMsg.isEmpty()) {
            photoStatusText.setText(fullMsg);
        } else {
            photoStatusText.setText("Your photo is approved!");
        }

        // Load the image
        if (photoUrl != null && !photoUrl.isEmpty()) {
            Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.error_image)
                    .into(photoPreview);
        } else if (photoId >= 0) {
            Timber.d("▶️ No URL passed, fetching bytes for photoId=%d", photoId);
            ApiService svc = RetrofitClient.getInstance().create(ApiService.class);
            svc.getPhotoStream(photoId)
                    .enqueue(new Callback<ResponseBody>() {
                        @Override public void onResponse(@NonNull Call<ResponseBody> call,
                                                         @NonNull Response<ResponseBody> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                try {
                                    byte[] data = response.body().bytes();
                                    Glide.with(AuctionSettingsActivity.this)
                                            .load(data)
                                            .placeholder(R.drawable.placeholder_image)
                                            .error(R.drawable.error_image)
                                            .into(photoPreview);
                                } catch (IOException e) {
                                    Timber.e(e, "Error reading image bytes");
                                    photoPreview.setImageResource(R.drawable.error_image);
                                }
                            } else {
                                photoPreview.setImageResource(R.drawable.error_image);
                            }
                        }
                        @Override public void onFailure(@NonNull Call<ResponseBody> call,
                                                        @NonNull Throwable t) {
                            Timber.e(t, "❌ Network error streaming photo");
                            photoPreview.setImageResource(R.drawable.error_image);
                        }
                    });
        } else {
            photoPreview.setImageResource(R.drawable.placeholder_image);
        }

        // Toggle price input when auctionable is checked
        checkAuctionable.setOnCheckedChangeListener((btn, isChecked) -> {
            editPurchaseNowPrice.setEnabled(!isChecked);
            if (isChecked) editPurchaseNowPrice.setText("");
        });

        // Save button handler
        btnSaveAuctionSettings.setOnClickListener(v -> saveAuctionSettings());
    }

    private void saveAuctionSettings() {
        boolean auctionable = checkAuctionable.isChecked();
        String priceText    = editPurchaseNowPrice.getText().toString().trim();

        // Validation
        if (!auctionable) {
            if (priceText.isEmpty()) {
                Toast.makeText(this,
                        "Please set a price or mark as auctionable.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (!validatePrice(priceText)) return;
        }

        // Disable UI
        btnSaveAuctionSettings.setEnabled(false);
        btnSaveAuctionSettings.setText("Saving…");

        ApiService svc = RetrofitClient.getInstance().create(ApiService.class);
        Call<Void> call = auctionable
                ? svc.setPhotoAuctionable(photoId, new com.example.staucktion.models.AuctionableRequest(true))
                : svc.setPurchasePrice(photoId, new com.example.staucktion.models.PriceRequest(Double.parseDouble(priceText)));

        call.enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> res) {
                resetSaveButton();
                if (res.isSuccessful()) {
                    Toast.makeText(AuctionSettingsActivity.this,
                            auctionable ? "Photo set auctionable!" : "Price saved!",
                            Toast.LENGTH_SHORT).show();
                    finishWithResult(auctionable);
                } else {
                    Toast.makeText(AuctionSettingsActivity.this,
                            "Save failed: HTTP " + res.code(),
                            Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                resetSaveButton();
                Toast.makeText(AuctionSettingsActivity.this,
                        "Network error: " + t.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resetSaveButton() {
        btnSaveAuctionSettings.setEnabled(true);
        btnSaveAuctionSettings.setText("Save");
    }

    private void finishWithResult(boolean auctionable) {
        Intent result = new Intent();
        result.putExtra("photo_id", photoId);
        result.putExtra("auctionable", auctionable);
        setResult(RESULT_OK, result);
        finish();
    }

    private boolean validatePrice(String text) {
        try {
            BigDecimal price = new BigDecimal(text);
            if (price.scale() > 2 || price.compareTo(BigDecimal.ZERO) <= 0 || price.compareTo(MAX_PRICE) > 0) {
                throw new IllegalArgumentException();
            }
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "Please enter a valid price.", Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
