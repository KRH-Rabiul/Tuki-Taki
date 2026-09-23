package com.marketbridge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversations", uniqueConstraints = @UniqueConstraint(columnNames = {"buyer_id", "seller_id", "product_id"}))
public class Conversation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long buyerId;
    private Long sellerId;
    private Long productId;
    private LocalDateTime updatedAt;

    public Conversation() {}
    public Long getId(){ return id; }
    public Long getBuyerId(){ return buyerId; }
    public void setBuyerId(Long v){ buyerId=v; }
    public Long getSellerId(){ return sellerId; }
    public void setSellerId(Long v){ sellerId=v; }
    public Long getProductId(){ return productId; }
    public void setProductId(Long v){ productId=v; }
    public LocalDateTime getUpdatedAt(){ return updatedAt; }
    public void setUpdatedAt(LocalDateTime v){ updatedAt=v; }
}
