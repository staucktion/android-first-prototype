package com.example.staucktion.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

public class RetrofitClient {
    private static RetrofitClient instance;
    // Change domain to local IP address when developing locally
    private static final String BASE_URL = "https://staucktion.com.tr/web-api/";
    private final Retrofit retrofit;
    private String authToken;  // dynamically updated after login

    private RetrofitClient() {
        // Logging interceptor
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        // Build OkHttpClient with a CookieJar and interceptors
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                // For bypassing hostname check when using SSL via IP connection instead of hostname
                .hostnameVerifier((hostname, session) -> true)
                .cookieJar(new CookieJar() {
                    private final HashMap<HttpUrl, List<Cookie>> cookieStore = new HashMap<>();

                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        cookieStore.put(url, cookies);
                    }

                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        List<Cookie> cookies = cookieStore.get(url);
                        return cookies != null ? cookies : new ArrayList<>();
                    }
                })
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder requestBuilder = original.newBuilder()
                            .header("Accept", "application/json")
                            .header("Content-Type", "application/json");

                    if (authToken != null && !authToken.isEmpty()) {
                        // Optionally add the Authorization header
                        requestBuilder.header("Authorization", "Bearer " + authToken);
                        // Add the token as a cookie to match middleware expectations
                        requestBuilder.header("Cookie", "token=" + authToken);
                    }

                    Request request = requestBuilder.build();
                    return chain.proceed(request);
                })
                .addInterceptor(logging)
                // timeout settings
                .connectTimeout(30, TimeUnit.SECONDS)  // Connection timeout
                .readTimeout(30, TimeUnit.SECONDS)     // Read timeout
                .writeTimeout(30, TimeUnit.SECONDS)    // Write timeout
                .build();

        Gson gson = new GsonBuilder().setLenient().create();

        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
    }

    public static synchronized RetrofitClient getInstance() {
        if (instance == null) {
            instance = new RetrofitClient();
        }
        return instance;
    }

    public <S> S create(Class<S> serviceClass) {
        return retrofit.create(serviceClass);
    }

    public void setAuthToken(String token) {
        this.authToken = token;
    }
}
