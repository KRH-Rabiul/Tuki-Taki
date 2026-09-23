package com.marketbridge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
public class Report {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long reporterId;
    private Long productId;
    private String reason;
    @Column(length = 1000) private String details;
    private String status;
    private LocalDateTime createdAt;
    public Long getId(){return id;} public Long getReporterId(){return reporterId;} public void setReporterId(Long v){reporterId=v;}
    public Long getProductId(){return productId;} public void setProductId(Long v){productId=v;}
    public String getReason(){return reason;} public void setReason(String v){reason=v;}
    public String getDetails(){return details;} public void setDetails(String v){details=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
