package com.example.staucktion;

import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.staucktion.api.ApiService;
import com.example.staucktion.api.RetrofitClient;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuctionSettingsActivity extends AppCompatActivity {

    private EditText editPurchaseNowPrice;
    private CheckBox checkAuctionable;
    private Button btnSaveAuctionSettings;
    private ImageView photoPreview;

    private int photoId;
    private String photoUrl;
    private String status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auction_settings);

        editPurchaseNowPrice = findViewById(R.id.editPurchaseNowPrice);
        checkAuctionable = findViewById(R.id.checkAuctionable);
        btnSaveAuctionSettings = findViewById(R.id.btnSaveAuctionSettings);
        photoPreview = findViewById(R.id.photoPreview);
        TextView photoStatusText = findViewById(R.id.photoStatusText);

        // Extract data from notification intent
        photoId = getIntent().getIntExtra("photo_id", -1);
        status = getIntent().getStringExtra("status");
        photoUrl = getIntent().getStringExtra("photo_url");

        photoStatusText.setText("Your photo was " + status + "!");

        // Load the photo into the preview
        Glide.with(this).load(photoUrl).into(photoPreview);

        MaterialCheckBox checkAuctionable = findViewById(R.id.checkAuctionable);
        TextInputEditText editPrice = findViewById(R.id.editPurchaseNowPrice);

        checkAuctionable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editPrice.setEnabled(!isChecked);
            if (isChecked) {
                editPrice.setText(""); // Clear the price if auctionable
            }
        });

        btnSaveAuctionSettings.setOnClickListener(v -> saveAuctionSettings());
    }

    private void saveAuctionSettings() {
        boolean isAuctionable = checkAuctionable.isChecked();
        String priceText = editPurchaseNowPrice.getText().toString().trim();

        if (!isAuctionable && priceText.isEmpty()) {
            Toast.makeText(this, "Please set a price or mark as auctionable.", Toast.LENGTH_SHORT).show();
            return;
        }

        double price = 0.0;
        if (!isAuctionable) {
            price = Double.parseDouble(priceText);
        }

        ApiService apiService = RetrofitClient.getInstance().create(ApiService.class);
        Call<ResponseBody> call = apiService.saveAuctionSettings(photoId, price, isAuctionable);

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(AuctionSettingsActivity.this, "Auction settings saved successfully!", Toast.LENGTH_SHORT).show();
                    finish(); // Go back after success
                } else {
                    Toast.makeText(AuctionSettingsActivity.this, "Failed to save settings.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Toast.makeText(AuctionSettingsActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
