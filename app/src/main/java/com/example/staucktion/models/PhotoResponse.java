package com.example.staucktion.models;

import com.google.gson.annotations.SerializedName;

public class PhotoResponse {
    private int id;

    @SerializedName("file_path")
    private String photoUrl;

    public int getId() { return id; }

    public String getPhotoUrl() {
        return photoUrl;
    }
}
