package com.example.staucktion.models;

public class PriceRequest {
    private double price;
    public PriceRequest(double price) { this.price = price; }
    public double getPrice()      { return price; }
    public void   setPrice(double p) { this.price = p; }
}

