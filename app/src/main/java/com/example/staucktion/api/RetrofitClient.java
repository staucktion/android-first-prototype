package com.example.staucktion.api;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private static RetrofitClient instance;
    private Retrofit retrofit;
    private String BASE_URL = "https://staucktion.com.tr/"; // Ensure this URL is correct

    // Private constructor ensures no external instantiation.
    public RetrofitClient() {
        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    // Synchronized singleton instance getter.
    public static synchronized RetrofitClient getInstance() {
        if (instance == null) {
            instance = new RetrofitClient();
        }
        return instance;
    }

    // Delegate create method.
    public <S> S create(Class<S> serviceClass) {
        return retrofit.create(serviceClass);
    }

    public String getBaseUrl() {
        return BASE_URL;
    }

    public void setBaseUrl(String newBaseUrl) {
        BASE_URL = newBaseUrl;
        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
}
