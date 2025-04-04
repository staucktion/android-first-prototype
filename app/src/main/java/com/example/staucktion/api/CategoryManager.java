package com.example.staucktion.api;

import com.example.staucktion.models.CategoryRequest;
import com.example.staucktion.models.CategoryResponse;
import retrofit2.Call;
import retrofit2.Callback;

public class CategoryManager {

    private final ApiService apiService;

    public CategoryManager() {
        this.apiService = RetrofitClient.getInstance().create(ApiService.class);
    }

    /**
     * Creates a new category with the given details.
     *
     * @param request  The category request object.
     * @param callback Callback to handle the response.
     */
    public void createCategory(CategoryRequest request, Callback<CategoryResponse> callback) {
        Call<CategoryResponse> call = apiService.createCategory(request);
        call.enqueue(callback);
    }
}
