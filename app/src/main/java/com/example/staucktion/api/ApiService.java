package com.example.staucktion.api;

import com.example.staucktion.models.AuthRequest;
import com.example.staucktion.models.AuthResponse;
import com.example.staucktion.models.CategoryRequest;
import com.example.staucktion.models.CategoryResponse;
import com.example.staucktion.models.LocationCreateResponse;
import com.example.staucktion.models.LocationRequest;

import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    @GET("health")
    Call<ResponseBody> getHealthStatus();

    @Multipart
    @POST("web-api/photos")
    Call<ResponseBody> uploadPhoto(
            @Part MultipartBody.Part photo,
            @Part("categoryId") RequestBody categoryId,
            @Part("deviceInfo") RequestBody deviceInfo
    );

    @POST("web-api/auth/google/android")
    Call<AuthResponse> loginWithGoogle(@Body AuthRequest authRequest);

    @POST("web-api/locations")
    Call<LocationCreateResponse> createLocation(@Body LocationRequest locationRequest);

    @POST("web-api/categories")
    Call<CategoryResponse> createCategory(@Body CategoryRequest categoryRequest);

    @GET("web-api/locations/{locationId}/categories")
    Call<List<CategoryResponse>> getCategoriesByLocationId(@Path("locationId") int locationId);

    /**
     * Retrieves approved categories by coordinates.
     * Expected query parameters: "latitude", "longitude" and "status".
     */
    @GET("web-api/categories/search/by-coordinates")
    Call<List<CategoryResponse>> getApprovedCategoriesByCoordinates(
            @Query("latitude") double latitude,
            @Query("longitude") double longitude,
            @Query("status") String status
    );
}
