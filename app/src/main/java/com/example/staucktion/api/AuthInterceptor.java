package com.example.staucktion.api;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {
    private Context context;

    public AuthInterceptor(Context context) {
        this.context = context;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        // Retrieve the app token from SharedPreferences.
        SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String token = prefs.getString("appToken", null);

        // Get the original request.
        Request originalRequest = chain.request();

        // If token is not available, proceed without modification.
        if (token == null) {
            return chain.proceed(originalRequest);
        }

        // Add the token as an Authorization header.
        Request newRequest = originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer " + token)
                .build();
        return chain.proceed(newRequest);
    }
}
