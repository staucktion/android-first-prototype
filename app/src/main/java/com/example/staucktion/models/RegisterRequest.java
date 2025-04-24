package com.example.staucktion.models;

import com.google.gson.annotations.SerializedName;

public class RegisterRequest {
    @SerializedName("email")
    private final String email;
    @SerializedName("password")
    private final String password;
    @SerializedName("first_name")
    private final String firstName;
    @SerializedName("last_name")
    private final String lastName;

    public RegisterRequest(String email, String password, String firstName, String lastName) {
        this.email     = email;
        this.password  = password;
        this.firstName = firstName;
        this.lastName  = lastName;
    }

    // getters if you need them
}
