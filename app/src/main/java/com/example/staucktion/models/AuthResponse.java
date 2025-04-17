package com.example.staucktion.models;

import com.google.gson.annotations.SerializedName;

public class AuthResponse {
    private String token; // instance variable
    private long expiresInMillis;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public long getExpiresInMillis() {
        return expiresInMillis;
    }

    public void setExpiresInMillis(long expiresInMillis) {
        this.expiresInMillis = expiresInMillis;
    }
    // Add this getter:
}
