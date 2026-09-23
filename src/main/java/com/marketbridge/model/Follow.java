package com.marketbridge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "follows", uniqueConstraints = @UniqueConstraint(columnNames = {"follower_id", "seller_id"}))
public class Follow {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long followerId;
    private Long sellerId;
    private LocalDateTime createdAt;

    public Follow() {}
    public Long getId(){ return id; }
    public Long getFollowerId(){ return followerId; }
    public void setFollowerId(Long v){ followerId=v; }
    public Long getSellerId(){ return sellerId; }
    public void setSellerId(Long v){ sellerId=v; }
    public LocalDateTime getCreatedAt(){ return createdAt; }
    public void setCreatedAt(LocalDateTime v){ createdAt=v; }
}
