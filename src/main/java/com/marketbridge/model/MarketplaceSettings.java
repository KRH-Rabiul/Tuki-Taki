package com.marketbridge.model;

import jakarta.persistence.*;

@Entity
@Table(name = "marketplace_settings")
public class MarketplaceSettings {
    @Id private Long id = 1L;
    private String pickupPoint = "Bonomaya";
    @Column(length = 1000) private String pickupInstructions = "Show your pickup code when collecting your order at Bonomaya.";
    public Long getId(){return id;} public String getPickupPoint(){return pickupPoint;} public void setPickupPoint(String v){pickupPoint=v;}
    public String getPickupInstructions(){return pickupInstructions;} public void setPickupInstructions(String v){pickupInstructions=v;}
}
