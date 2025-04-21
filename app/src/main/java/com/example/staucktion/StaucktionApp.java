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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OneSignal.promptForPushNotifications();
        }

        // 3) Log current subscription state
        OSDeviceState state = OneSignal.getDeviceState();
        if (state != null) {
            Timber.d("OneSignal subscribed: %s", state.isSubscribed());
            Timber.d("OneSignal pushToken: %s", state.getPushToken());
            Timber.d("OneSignal userId: %s", state.getUserId());
        }

        // 4) Handle notification opens
        OneSignal.setNotificationOpenedHandler(
                new OneSignal.OSNotificationOpenedHandler() {
                    @Override
                    public void notificationOpened(OSNotificationOpenedResult result) {
                        JSONObject data = result.getNotification().getAdditionalData();
                        if (data == null) return;

                        int photoId = data.optInt("photo_id", -1);
                        String photoUrl = data.optString("photo_url", "");
                        String status = data.optString("status", "");

                        if (photoId >= 0) {
                            Intent intent = new Intent(
                                    getApplicationContext(),
                                    AuctionSettingsActivity.class
                            ).addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK |
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                            );
                            intent.putExtra("photo_id", photoId);
                            intent.putExtra("photo_url", photoUrl);
                            intent.putExtra("status", status);
                            Timber.d("🔍 AuctionSettingsActivity received photo_id=%d, photo_url=%s, status=%s",
                                    photoId, photoUrl, status);

                            startActivity(intent);
                        }
                    }
                }
        );
    }
}