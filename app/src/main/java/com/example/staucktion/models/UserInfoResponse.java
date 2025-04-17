package com.example.staucktion.models;

import com.google.gson.annotations.SerializedName;

public class UserInfoResponse {
    @SerializedName("user")
    private InnerUser user;

    public static class InnerUser {
        @SerializedName("id")
        private int userId;

        private String first_name;

        private String last_name;

        public int getUserId() { return userId; }
    }

    public InnerUser getUser() { return user; }
}
