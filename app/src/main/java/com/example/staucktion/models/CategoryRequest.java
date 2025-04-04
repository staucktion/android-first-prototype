package com.example.staucktion.models;

public class CategoryRequest {
    private String name;
    private String address;
    private double valid_radius;
    private int location_id;
    private int status_id;

    public CategoryRequest(String name, String address, double valid_radius, int location_id, int status_id) {
        this.name = name;
        this.address = address;
        this.valid_radius = valid_radius;
        this.location_id = location_id;
        this.status_id = status_id;
    }

    public String getName() {
        return name;
    }
}
