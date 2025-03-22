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

    // Health check (if your backend has a /health endpoint)
    @GET("health")
    Call<ResponseBody> getHealthStatus();

    /**
     * Upload a photo to the server, passing the category ID in the request.
     */
    @Multipart
    @POST("web-api/photos")
    Call<ResponseBody> uploadPhoto(
            @Part MultipartBody.Part photo,
            @Part("categoryId") RequestBody categoryId,
            @Part("deviceInfo") RequestBody deviceInfo
    );

    /**
     * Google Sign-In authentication.
     */
    @POST("web-api/auth/google/android")
    Call<AuthResponse> loginWithGoogle(@Body AuthRequest authRequest);

    /**
     * Create a new location by posting a LocationRequest (latitude, longitude).
     */
    @POST("web-api/locations")
    Call<LocationCreateResponse> createLocation(@Body LocationRequest locationRequest);

    /**
     * Create a new category by posting a CategoryRequest.
     */
    @POST("web-api/categories")
    Call<CategoryResponse> createCategory(@Body CategoryRequest categoryRequest);

    /**
     * (Optional) Example of retrieving categories by location ID.
     * If your backend supports this, you can use it to list categories for a specific location.
     */
    @GET("web-api/locations/{locationId}/categories")
    Call<List<CategoryResponse>> getCategoriesByLocationId(@Path("locationId") int locationId);

    /**
     * (Optional) Example: find a category by coordinates (latitude, longitude).
     * Adjust the endpoint path and query parameters to match your server’s route.
     *
     * For example, if your backend endpoint is:
     *   GET /web-api/categories/findByCoordinates?lat=XX&lon=YY
     * Then do:
     */
    @GET("web-api/categories/search/by-coordinates")
    Call<CategoryResponse> getCategoryByCoordinates(
            @Query("lat") double lat,
            @Query("lon") double lon
    );

    /**
     * (Optional) Example: create a category for a given location via a special endpoint.
     * If your backend uses something like:
     *   POST /web-api/locations/{locationId}/categories?name=XYZ
     * you could define:
     *
     * @POST("web-api/locations/{locationId}/categories")
     * Call<CategoryResponse> createCategoryForLocation(
     *     @Path("locationId") int locationId,
     *     @Query("name") String categoryName
     * );
     *
     * Adjust as needed to match your backend.
     */
}
