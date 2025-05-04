package com.example.staucktion;

import static com.example.staucktion.R.layout.activity_photo_rejected;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.staucktion.api.ApiService;
import com.example.staucktion.api.RetrofitClient;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

public class PhotoRejectedActivity extends AppCompatActivity {
    private ImageView ivRejectedPhoto;
    private TextView  tvRejectionMessage;
    private Button    btnRetry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_rejected);

        ivRejectedPhoto    = findViewById(R.id.ivRejectedPhoto);
        tvRejectionMessage = findViewById(R.id.tvRejectionMessage);
        btnRetry           = findViewById(R.id.btnRetry);

        Intent intent      = getIntent();
        int    photoId     = intent.getIntExtra("notification_photo_id", -1);
        String fullMsg     = intent.getStringExtra("notification_message");

        // Show the exact notification text if we got one, otherwise a default
        if (fullMsg != null && !fullMsg.isEmpty()) {
            tvRejectionMessage.setText(fullMsg);
        } else {
            tvRejectionMessage.setText(
                    "Sorry, your photo is rejected.\n" +
                            "Please review the guidelines and try again."
            );
        }

        // Stream the image by ID (no URL required)
        if (photoId >= 0) {
            ApiService svc = RetrofitClient.getInstance().create(ApiService.class);
            svc.getPhotoStream(photoId).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call,
                                       @NonNull Response<ResponseBody> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            byte[] data = response.body().bytes();
                            Glide.with(PhotoRejectedActivity.this)
                                    .load(data)
                                    .placeholder(R.drawable.placeholder_image)
                                    .error(R.drawable.error_image)
                                    .into(ivRejectedPhoto);
                        } catch (IOException e) {
                            ivRejectedPhoto.setImageResource(R.drawable.error_image);
                            Timber.e(e, "Error reading image bytes");
                        }
                    } else {
                        ivRejectedPhoto.setImageResource(R.drawable.error_image);
                    }
                }
                @Override
                public void onFailure(@NonNull Call<ResponseBody> call,
                                      @NonNull Throwable t) {
                    ivRejectedPhoto.setImageResource(R.drawable.error_image);
                    Timber.e(t, "Network error fetching rejected photo");
                }
            });
        } else {
            ivRejectedPhoto.setImageResource(R.drawable.placeholder_image);
        }

        btnRetry.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
    }
}