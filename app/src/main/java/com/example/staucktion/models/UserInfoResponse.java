package com.example.staucktion.models;

import com.google.gson.annotations.SerializedName;

public class UserInfoResponse {
    @SerializedName("user")
    private InnerUser user;

    public static class InnerUser {
        @SerializedName("id")
        private int userId;

        @SerializedName("first_name")

        private String firstName;

        @SerializedName("last_name")

        private String lastName;
        @SerializedName("profile_picture")

        private String photoUrl;        // <-- add this

        public int getUserId() { return userId; }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public String getPhotoUrl() {
            return photoUrl;
        }
    }

    public InnerUser getUser() { return user; }

}
