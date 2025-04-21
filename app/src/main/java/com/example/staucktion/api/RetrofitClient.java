package com.example.staucktion.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import timber.log.Timber;

public class RetrofitClient {
    private static RetrofitClient instance;
    private static final String BASE_URL = "https://staucktion.com.tr/web-api/";
    private final Retrofit retrofit;
    private String authToken;  // will be set after login

    private RetrofitClient() {
        // 1) Create your logging interceptor once
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(msg ->
                Timber.tag("OkHttp").d(msg)
        );
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        // 2) Build OkHttpClient
        OkHttpClient client = new OkHttpClient.Builder()
                // if you’re hitting an IP rather than hostname
                .hostnameVerifier((hostname, session) -> true)

                // a simple in‑memory CookieJar
                .cookieJar(new CookieJar() {
                    private final Map<HttpUrl, List<Cookie>> store = new HashMap<>();

                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        store.put(url, cookies);
                    }

                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        List<Cookie> cookies = store.get(url);
                        return cookies != null ? cookies : new ArrayList<>();
                    }
                })

                // your custom interceptor for headers + auth
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder rb = original.newBuilder()
                            .header("Accept", "application/json")
                            .header("Content-Type", "application/json");
                    if (authToken != null && !authToken.isEmpty()) {
                        rb.header("Authorization", "Bearer " + authToken)
                                .header("Cookie", "token=" + authToken);
                    }
                    return chain.proceed(rb.build());
                })

                // 3) Add logging at both application and network levels
                .addInterceptor(logging)
                .addNetworkInterceptor(logging)

                // 4) Configure timeouts
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        // 5) Build Retrofit
        Gson gson = new GsonBuilder()
                .setLenient()
                .create();

        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
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

    /** Call this immediately after login to inject the JWT */
    public void setAuthToken(String token) {
        this.authToken = token;
    }
}