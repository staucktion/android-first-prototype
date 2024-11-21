package com.example.staucktion.api;

import retrofit2.Call;
import retrofit2.http.GET;
import okhttp3.ResponseBody;

public interface ApiService {
    // Define a GET request for the /health endpoint
    @GET("health")
    Call<ResponseBody> getHealthStatus();
}
