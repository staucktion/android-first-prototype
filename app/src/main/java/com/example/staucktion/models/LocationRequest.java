package com.example.staucktion.models;

public class LocationRequest {
    private String latitude;
    private String longitude;

    public LocationRequest(double latitude, double longitude) {
        // Convert numeric values to String
        this.latitude = String.valueOf(latitude);
        this.longitude = String.valueOf(longitude);
    }

    public String getLatitude() {
        return latitude;
    }

    public String getLongitude() {
        return longitude;
    }
}
