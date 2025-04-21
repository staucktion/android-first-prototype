package com.example.staucktion.models;

public class AuctionableRequest {
    private boolean auctionable;
    public AuctionableRequest(boolean auctionable) {
        this.auctionable = auctionable;
    }
    public boolean isAuctionable() { return auctionable; }
    public void setAuctionable(boolean auctionable) {
        this.auctionable = auctionable;
    }
}


