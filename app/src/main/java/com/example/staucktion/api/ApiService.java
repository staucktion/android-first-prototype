package com.example.staucktion.api;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface ApiService {
    // Define a GET request for the /health endpoint
    @GET("health")
    Call<ResponseBody> getHealthStatus();

    @Multipart
    @POST("web-api/photos")// /photo/upload for https://ctis.goktug.dev/
    Call<ResponseBody> uploadPhoto(@Part MultipartBody.Part photo);
}
