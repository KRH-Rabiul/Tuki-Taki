package com.marketbridge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// One row = one campus discussion/request post (e.g. "Looking for a neck pillow").
// authorName is a snapshot so old posts still show a name if the account changes later.
@Entity
@Table(name = "community_posts")
public class CommunityPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long authorId;

    private String authorName;

    private String title;

    @Column(length = 2000)
    private String body;

    private int viewCount;

    private int replyCount; // kept in sync by CommunityController whenever a reply is added/removed

    private LocalDateTime createdAt;

    public CommunityPost() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public int getViewCount() { return viewCount; }
    public void setViewCount(int viewCount) { this.viewCount = viewCount; }
    public int getReplyCount() { return replyCount; }
    public void setReplyCount(int replyCount) { this.replyCount = replyCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
