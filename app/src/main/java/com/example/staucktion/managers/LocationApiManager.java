package com.example.staucktion.managers;

import com.example.staucktion.api.ApiService;
import com.example.staucktion.api.RetrofitClient;
import com.example.staucktion.models.LocationCreateResponse;
import com.example.staucktion.models.LocationRequest;

import retrofit2.Call;
import retrofit2.Callback;

public class LocationApiManager {
    private ApiService apiService;

    public LocationApiManager() {
        apiService = RetrofitClient.getInstance().create(ApiService.class);
    }

    public void createLocation(double latitude, double longitude, Callback<LocationCreateResponse> callback) {
        LocationRequest request = new LocationRequest(latitude, longitude);
        Call<LocationCreateResponse> call = apiService.createLocation(request);
        call.enqueue(callback);
    }
}
