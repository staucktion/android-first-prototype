package com.example.staucktion.api;

import com.example.staucktion.models.AuthRequest;
import com.example.staucktion.models.AuthResponse;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface ApiService {
    // Define a GET request for the /health endpoint
    @GET("health")
    Call<ResponseBody> getHealthStatus();

    @Multipart
    @POST("web-api/photos")
    Call<ResponseBody> uploadPhoto(@Part MultipartBody.Part photo);

    // Adjust the endpoint path ("auth/google") to match your backend
    @POST("auth/google")
    Call<AuthResponse> loginWithGoogle(@Body AuthRequest request);
}
