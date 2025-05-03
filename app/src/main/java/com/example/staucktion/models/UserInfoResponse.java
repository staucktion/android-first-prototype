package com.example.staucktion.models;

import com.google.gson.annotations.SerializedName;

public class UserInfoResponse {
    @SerializedName("user")
    private User user;

    public User getUser() {
        return user;
    }

    public static class User {
        @SerializedName("id")
        private int userId;

        @SerializedName("first_name")
        private String firstName;

        @SerializedName("last_name")
        private String lastName;

        @SerializedName("profile_picture")
        private String photoUrl;

        @SerializedName("role_id")
        private int roleId;

        @SerializedName("user_role")
        private UserRole userRole;

        public int getUserId() {
            return userId;
        }
        public String getFirstName() {
            return firstName;
        }
        public String getLastName() {
            return lastName;
        }
        public String getPhotoUrl() {
            return photoUrl;
        }
        public int getRoleId() {
            return roleId;
        }
        public UserRole getUserRole() {
            return userRole;
        }

        /** Returns the string “admin” / “validator” / etc. */
        public String getRoleName() {
            return userRole != null
                    ? userRole.getRole()
                    : null;
        }
    }

    public static class UserRole {
        @SerializedName("id")
        private int id;

        @SerializedName("role")
        private String role;

        public int getId() {
            return id;
        }
        public String getRole() {
            return role;
        }
    }
}
