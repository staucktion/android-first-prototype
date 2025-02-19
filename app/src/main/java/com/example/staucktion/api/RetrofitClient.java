package com.example.staucktion.api;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private String BASE_URL = "https://staucktion.com.tr/";// used to be http://192.168.1.200/
    // https://ctis.goktug.dev/
    private Retrofit retrofit;

    public RetrofitClient() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
    }

    public Retrofit getInstance() {
        return retrofit;
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
