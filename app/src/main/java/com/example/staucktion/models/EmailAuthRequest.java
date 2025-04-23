package com.example.staucktion.models;

public class EmailAuthRequest {
    private final String email;
    private final String password;

    public EmailAuthRequest(String email, String password) {
        this.email    = email;
        this.password = password;
    }

    // getters if you need them
}
