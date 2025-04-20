package com.example.staucktion.models;

public class AuctionRequest {
    private int photoId;
    public AuctionRequest(int photoId) {
        this.photoId = photoId;
    }
    public int getPhotoId() { return photoId; }
    public void setPhotoId(int photoId) { this.photoId = photoId; }
}

