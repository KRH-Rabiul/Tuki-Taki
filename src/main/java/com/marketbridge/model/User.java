package com.marketbridge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// One table holds marketplace members and administrators.
// A normal USER can both buy and sell; ADMIN manages the platform.
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true)
    private String email;

    private String password; // stored as a PBKDF2 hash (with legacy SHA-256 read support), never plain text

    private String role;   // "USER" or "ADMIN" (older BUYER/SELLER accounts remain supported)

    private String status; // "APPROVED", "PENDING", or "REJECTED"

    private String token; // random login session token, set fresh on every successful login

    @Column(length = 500)
    private String bio;

    private String phone;

    @Column(length = 500)
    private String profileImageUrl;

    @Column(length = 120)
    private String shopName;

    @Column(length = 1000)
    private String shopDescription;

    private boolean verified;

    private String resetToken; // one-time token for "forgot password", null when not in a reset flow

    private LocalDateTime resetTokenExpiry; // resetToken is only valid until this moment

    public User() {
    }

    // ---- Getters and Setters ----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }
    public String getShopDescription() { return shopDescription; }
    public void setShopDescription(String shopDescription) { this.shopDescription = shopDescription; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public String getResetToken() { return resetToken; }
    public void setResetToken(String resetToken) { this.resetToken = resetToken; }
    public LocalDateTime getResetTokenExpiry() { return resetTokenExpiry; }
    public void setResetTokenExpiry(LocalDateTime resetTokenExpiry) { this.resetTokenExpiry = resetTokenExpiry; }
}
