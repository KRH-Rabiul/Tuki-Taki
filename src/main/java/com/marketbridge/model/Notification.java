package com.marketbridge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String title;
    @Column(length = 1000) private String message;
    private String linkUrl;
    private boolean readStatus;
    private LocalDateTime createdAt;
    public Long getId() { return id; } public Long getUserId() { return userId; } public void setUserId(Long v) { userId=v; }
    public String getTitle() { return title; } public void setTitle(String v) { title=v; }
    public String getMessage() { return message; } public void setMessage(String v) { message=v; }
    public String getLinkUrl() { return linkUrl; } public void setLinkUrl(String v) { linkUrl=v; }
    public boolean isReadStatus() { return readStatus; } public void setReadStatus(boolean v) { readStatus=v; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt=v; }
}
