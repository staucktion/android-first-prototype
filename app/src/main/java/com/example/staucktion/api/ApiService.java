package com.example.staucktion.api;

import com.example.staucktion.models.AuctionableRequest;
import com.example.staucktion.models.AuthRequest;
import com.example.staucktion.models.AuthResponse;
import com.example.staucktion.models.CategoryRequest;
import com.example.staucktion.models.CategoryResponse;
import com.example.staucktion.models.LocationCreateResponse;
import com.example.staucktion.models.LocationRequest;
import com.example.staucktion.models.PriceRequest;
import com.example.staucktion.models.UserInfoResponse;

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
import retrofit2.http.Streaming;

public interface ApiService {

    @GET("health")
    Call<ResponseBody> getHealthStatus();

    @Multipart
    @POST("photos")
    Call<ResponseBody> uploadPhoto(
            @Part MultipartBody.Part photo,
            @Part("categoryId") RequestBody categoryId,
            @Part("deviceInfo") RequestBody deviceInfo
    );

    @GET("photos/{photoId}")
    @Streaming
    Call<ResponseBody> getPhotoStream(@Path("photoId") int photoId);

    @POST("auth/google/android")
    Call<AuthResponse> loginWithGoogle(@Body AuthRequest authRequest);

    // ← new auth/info endpoint
    @POST("auth/info")
    Call<UserInfoResponse> getUserInfo();

    @POST("locations")
    Call<LocationCreateResponse> createLocation(@Body LocationRequest locationRequest);

    @POST("categories")
    Call<CategoryResponse> createCategory(@Body CategoryRequest categoryRequest);

    @GET("locations/{locationId}/categories")
    Call<List<CategoryResponse>> getCategoriesByLocationId(@Path("locationId") int locationId);

    /**
     * Retrieves approved categories by coordinates.
     * Expected query parameters: "latitude", "longitude" and "status".
     */
    @GET("categories/search/by-coordinates")
    Call<List<CategoryResponse>> getApprovedCategoriesByCoordinates(
            @Query("latitude") double latitude,
            @Query("longitude") double longitude,
            @Query("status") String status
    );
    @POST("photos/{photoId}/price")
    Call<ResponseBody> setPurchasePrice(
            @Path("photoId")   int            photoId,
            @Body PriceRequest body
    );

    @POST("photos/{photoId}/auctionable")
    Call<ResponseBody> markPhotoAuctionable(
            @Path("photoId") int photoId,
            @Body AuctionableRequest body );
}
