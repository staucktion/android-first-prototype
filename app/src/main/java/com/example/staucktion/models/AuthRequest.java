package com.example.staucktion.models;

import com.google.gson.annotations.SerializedName;

public class AuthRequest {
    @SerializedName("id_token")
    private String idToken;

    public AuthRequest(String idToken) {
        this.idToken = idToken;
    }

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }
}
