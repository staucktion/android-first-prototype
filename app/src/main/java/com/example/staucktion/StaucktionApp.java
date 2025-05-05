// src/main/java/com/example/staucktion/StaucktionApp.java
package com.example.staucktion;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

import com.onesignal.BuildConfig;
import com.onesignal.OSDeviceState;
import com.onesignal.OSNotificationOpenedResult;
import com.onesignal.OneSignal;

import org.json.JSONObject;

import timber.log.Timber;

public class StaucktionApp extends Application {

    private static final String ONESIGNAL_APP_ID =
            "5dafc689-25b8-4756-8c78-a31e6a541e51";

    @Override
    public void onCreate() {
        super.onCreate();

        // 1) Plant Timber only in DEBUG builds
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }

        // 2) Initialize OneSignal
        OneSignal.initWithContext(this);
        OneSignal.setAppId(ONESIGNAL_APP_ID);

        // 3) Log current subscription state
        OSDeviceState state = OneSignal.getDeviceState();
        if (state != null) {
            Timber.d("OneSignal subscribed: %s", state.isSubscribed());
            Timber.d("OneSignal pushToken: %s", state.getPushToken());
            Timber.d("OneSignal userId: %s", state.getUserId());
        }

        // 4) Handle notification opens
        OneSignal.setNotificationOpenedHandler(result -> {
            // 1) pull the raw body text
            String fullMessage = result
                    .getNotification()
                    .getBody();   // e.g. "Your photo has been rejected with the following reason: bad photo"

            JSONObject data = result.getNotification().getAdditionalData();
            if (data == null) return;

            int    photoId = data.optInt("photo_id", -1);
            String action  = data.optString("action", "");

            if (photoId < 0) return;

            Class<?> target;
            if ("approve".equalsIgnoreCase(action)) {
                target = AuctionSettingsActivity.class;
            }
            else if ("reject".equalsIgnoreCase(action)
                    || "rejected".equalsIgnoreCase(action)) {
                target = PhotoRejectedActivity.class;
            }
            else {
                target = MainActivity.class;  // fallback
            }

            Intent i = new Intent(getApplicationContext(), target)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("notification_photo_id",  photoId)
                    .putExtra("notification_action",    action)
                    .putExtra("notification_message",   fullMessage);
            startActivity(i);
        });
    }
}