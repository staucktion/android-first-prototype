package com.example.staucktion.models;

public class AuthResponse {
    private String token; // or any fields your backend returns

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
