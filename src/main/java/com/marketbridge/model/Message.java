package com.marketbridge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
public class Message {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long conversationId;
    private Long senderId;
    private Long receiverId;
    @Column(length = 2000, nullable = false)
    private String content;
    private boolean readStatus;
    private LocalDateTime createdAt;

    public Message() {}
    public Long getId(){ return id; }
    public Long getConversationId(){ return conversationId; }
    public void setConversationId(Long v){ conversationId=v; }
    public Long getSenderId(){ return senderId; }
    public void setSenderId(Long v){ senderId=v; }
    public Long getReceiverId(){ return receiverId; }
    public void setReceiverId(Long v){ receiverId=v; }
    public String getContent(){ return content; }
    public void setContent(String v){ content=v; }
    public boolean isReadStatus(){ return readStatus; }
    public void setReadStatus(boolean v){ readStatus=v; }
    public LocalDateTime getCreatedAt(){ return createdAt; }
    public void setCreatedAt(LocalDateTime v){ createdAt=v; }
}
