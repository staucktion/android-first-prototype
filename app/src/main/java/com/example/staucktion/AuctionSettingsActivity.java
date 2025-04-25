package com.example.staucktion;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.staucktion.api.ApiService;
import com.example.staucktion.api.RetrofitClient;
import com.example.staucktion.models.AuctionableRequest;
import com.example.staucktion.models.PriceRequest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

import java.io.IOException;
import java.math.BigDecimal;

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
    private String status;
    private static final BigDecimal MAX_PRICE = new BigDecimal("1000000.00");  // 1 million

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auction_settings);

        // 1) Bind views
        editPurchaseNowPrice   = findViewById(R.id.editPurchaseNowPrice);
        checkAuctionable       = findViewById(R.id.checkAuctionable);
        btnSaveAuctionSettings = findViewById(R.id.btnSaveAuctionSettings);
        photoPreview           = findViewById(R.id.photoPreview);
        photoStatusText        = findViewById(R.id.photoStatusText);

        // 2) Extract Intent extras
        Intent intent = getIntent();
        photoId  = intent.getIntExtra("photo_id", -1);
        status   = intent.getStringExtra("status");
        photoUrl = intent.getStringExtra("photo_url");

        Timber.d("🔍 Intent extras → photo_id=%d, status=%s, photo_url=%s",
                photoId, status, photoUrl);

        // 3) Update status text
        if (status == null || status.isEmpty()) {
            status = "approved";
        }
        photoStatusText.setText("Your photo is " + status + "!");

        // 4) Load the image
        if (photoUrl != null && !photoUrl.isEmpty()) {
            // we already have a full URL
            Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.error_image)
                    .into(photoPreview);

        } else if (photoId >= 0) {
            Timber.d("▶️ No URL passed, fetching bytes for photoId=%d", photoId);
            ApiService svc = RetrofitClient
                    .getInstance()
                    .create(ApiService.class);

            svc.getPhotoStream(photoId)
                    .enqueue(new Callback<ResponseBody>() {
                        @Override
                        public void onResponse(@NonNull Call<ResponseBody> call,
                                               @NonNull Response<ResponseBody> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                try {
                                    // read all bytes
                                    byte[] data = response.body().bytes();

                                    // now Glide can decode the byte[]
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
                                Timber.w("⚠️ Failed to stream image, HTTP %d", response.code());
                                photoPreview.setImageResource(R.drawable.error_image);
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ResponseBody> call,
                                              @NonNull Throwable t) {
                            Timber.e(t, "❌ Network error streaming photo");
                            photoPreview.setImageResource(R.drawable.error_image);
                        }
                    });

        } else {
            // neither ID nor URL → show placeholder
            photoPreview.setImageResource(R.drawable.placeholder_image);
        }

        // 5) Toggle price input when auctionable is checked
        checkAuctionable.setOnCheckedChangeListener((btn, isChecked) -> {
            editPurchaseNowPrice.setEnabled(!isChecked);
            if (isChecked) editPurchaseNowPrice.setText("");
        });

        // 6) Save button
        btnSaveAuctionSettings.setOnClickListener(v -> saveAuctionSettings());
    }

    private void saveAuctionSettings() {
        boolean auctionable = checkAuctionable.isChecked();
        String priceText   = editPurchaseNowPrice.getText().toString().trim();

        // validation
        if (!auctionable) {
            if (priceText.isEmpty()) {
                Toast.makeText(this,
                        "Please set a price or mark as auctionable.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
            if (!validatePrice(priceText)) {
                // validatePrice already shows its own error toast
                return;
            }
        }

        // Disable UI
        btnSaveAuctionSettings.setEnabled(false);
        btnSaveAuctionSettings.setText("Saving…");

        // Pick the right call
        Call<Void> call;
        if (auctionable) {
            call = RetrofitClient.getInstance()
                    .create(ApiService.class)
                    .setPhotoAuctionable(photoId, new AuctionableRequest(true));
        } else {
            double price;
            try {
                price = Double.parseDouble(priceText);
            } catch (NumberFormatException e) {
                Toast.makeText(this,
                        "Invalid price format",
                        Toast.LENGTH_SHORT
                ).show();
                btnSaveAuctionSettings.setEnabled(true);
                btnSaveAuctionSettings.setText("Save");
                return;
            }
            call = RetrofitClient.getInstance()
                    .create(ApiService.class)
                    .setPurchasePrice(photoId, new PriceRequest(price));
        }

        // Fire-and-forget, Retrofit does IO off the main thread automatically
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> res) {
                btnSaveAuctionSettings.setEnabled(true);
                btnSaveAuctionSettings.setText("Save");

                if (res.isSuccessful()) {
                    Toast.makeText(AuctionSettingsActivity.this,
                            auctionable
                                    ? "Photo set auctionable!"
                                    : "Price saved!",
                            Toast.LENGTH_SHORT
                    ).show();
                    finishWithResult(auctionable);
                } else {
                    Toast.makeText(AuctionSettingsActivity.this,
                            "Save failed: HTTP " + res.code(),
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                btnSaveAuctionSettings.setEnabled(true);
                btnSaveAuctionSettings.setText("Save");
                Toast.makeText(AuctionSettingsActivity.this,
                        "Network error: " + t.getMessage(),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    // send back to your caller which “tab” to show
    private void finishWithResult(boolean auctionable) {
        Intent result = new Intent();
        result.putExtra("photo_id", photoId);
        result.putExtra("auctionable", auctionable);
        setResult(RESULT_OK, result);
        finish();
    }

    private boolean validatePrice(String text) {
        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter a price.", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            BigDecimal price = new BigDecimal(text);
            if (price.scale() > 2) {
                Toast.makeText(this, "Please enter a valid amount (up to 2 decimals).", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                Toast.makeText(this, "Price must be greater than zero.", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (price.compareTo(MAX_PRICE) > 0) {
                Toast.makeText(this, "Price cannot exceed " + MAX_PRICE.toPlainString(), Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid price format.", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

}
