package com.marketbridge.controller;

import com.marketbridge.model.Product;
import com.marketbridge.model.Review;
import com.marketbridge.model.User;
import com.marketbridge.repository.ProductRepository;
import com.marketbridge.repository.ReviewRepository;
import com.marketbridge.repository.UserRepository;
import com.marketbridge.repository.FollowRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;

    // Fixed, absolute folder (see app.upload-dir in application.properties) -
    // deliberately NOT relative to the project folder, so uploaded photos
    // survive extracting a fresh project zip into a new folder.
    @Value("${app.upload-dir}")
    private String uploadDir;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private FollowRepository followRepository;

    @GetMapping("/{id}/seller-profile")
    public ResponseEntity<?> sellerProfile(@PathVariable Long id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Seller not found"));
        }

        User user = userOpt.get();
        if ("ADMIN".equals(user.getRole())) {
            return ResponseEntity.status(404).body(Map.of("message", "Seller not found"));
        }

        // Public seller information must only count public/active listings.
        List<Product> activeProducts = productRepository.findBySellerIdAndAvailability(id, "ACTIVE");
        List<Long> productIds = activeProducts.stream().map(Product::getId).filter(java.util.Objects::nonNull).toList();
        List<Review> reviews = productIds.isEmpty()
                ? List.of()
                : reviewRepository.findByProductIdIn(productIds);

        double rating = reviews.stream().mapToInt(Review::getRating).average().orElse(0);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("name", user.getName() == null ? "" : user.getName());
        result.put("bio", user.getBio() == null ? "" : user.getBio());
        result.put("shopName", user.getShopName() == null || user.getShopName().isBlank()
                ? (user.getName() == null || user.getName().isBlank() ? "Tuki-Taki Shop" : user.getName() + "'s Shop")
                : user.getShopName());
        result.put("shopDescription", user.getShopDescription() == null ? "" : user.getShopDescription());
        result.put("profileImageUrl", user.getProfileImageUrl() == null ? "" : user.getProfileImageUrl());
        result.put("phone", user.getPhone() == null ? "" : user.getPhone());
        result.put("verified", user.isVerified());
        result.put("activeListings", activeProducts.size());
        result.put("rating", Math.round(rating * 10.0) / 10.0);
        result.put("reviewCount", reviews.size());
        result.put("followers", followRepository.countBySellerId(id));
        return ResponseEntity.ok(result);
    }

    // Authenticated shortcut used by the owner's My Shop page.
    @GetMapping("/me/seller-profile")
    public ResponseEntity<?> mySellerProfile(HttpServletRequest request) {
        User current = (User) request.getAttribute("currentUser");
        if (current == null || "ADMIN".equals(current.getRole())) {
            return ResponseEntity.status(403).body(Map.of("message", "Marketplace account required"));
        }
        return sellerProfile(current.getId());
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        User current = (User) request.getAttribute("currentUser");
        if (current == null || "ADMIN".equals(current.getRole())) return ResponseEntity.status(403).body(Map.of("message", "Marketplace account required"));
        return ResponseEntity.ok(safe(current));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(HttpServletRequest request, @RequestBody Map<String, String> body) {
        User current = (User) request.getAttribute("currentUser");
        if (current == null || "ADMIN".equals(current.getRole())) return ResponseEntity.status(403).body(Map.of("message", "Marketplace account required"));
        if (body.containsKey("name")) current.setName(trim(body.get("name"), 80));
        if (body.containsKey("bio")) current.setBio(trim(body.get("bio"), 500));
        if (body.containsKey("phone")) current.setPhone(trim(body.get("phone"), 30));
        if (current.getName() == null || current.getName().isBlank()) return ResponseEntity.badRequest().body(Map.of("message", "Name is required"));
        userRepository.save(current);
        return ResponseEntity.ok(safe(current));
    }

    @PutMapping("/me/shop")
    public ResponseEntity<?> updateShop(HttpServletRequest request, @RequestBody Map<String, String> body) {
        User current = (User) request.getAttribute("currentUser");
        if (current == null || "ADMIN".equals(current.getRole())) return ResponseEntity.status(403).body(Map.of("message", "Marketplace account required"));
        current.setShopName(trim(body.getOrDefault("shopName", ""), 120));
        current.setShopDescription(trim(body.getOrDefault("shopDescription", ""), 1000));
        userRepository.save(current);
        return ResponseEntity.ok(safe(current));
    }

    @PostMapping(value = "/me/profile-photo", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadProfilePhoto(HttpServletRequest request, @RequestParam("image") MultipartFile image) {
        User current = (User) request.getAttribute("currentUser");
        if (current == null || "ADMIN".equals(current.getRole())) return ResponseEntity.status(403).body(Map.of("message", "Marketplace account required"));
        if (image == null || image.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "Choose an image"));
        if (image.getSize() > 5 * 1024 * 1024) return ResponseEntity.badRequest().body(Map.of("message", "Image must be 5 MB or smaller"));
        String contentType = image.getContentType();
        if (contentType == null || !List.of("image/jpeg", "image/png", "image/webp").contains(contentType)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only JPG, PNG, and WebP images are allowed"));
        }
        try {
            File dir = new File(uploadDir);
            if (!dir.exists() && !dir.mkdirs()) return ResponseEntity.status(500).body(Map.of("message", "Could not create upload folder"));
            String extension = switch (contentType) { case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> ".jpg"; };
            String filename = "profile-" + UUID.randomUUID() + extension;
            Path target = Path.of(uploadDir, filename);
            Files.copy(image.getInputStream(), target);
            current.setProfileImageUrl("/uploads/" + filename);
            userRepository.save(current);
            return ResponseEntity.ok(Map.of("profileImageUrl", current.getProfileImageUrl()));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("message", "Profile photo upload failed"));
        }
    }

    private Map<String,Object> safe(User u) {
        return Map.ofEntries(
                Map.entry("id", u.getId()), Map.entry("name", u.getName() == null ? "" : u.getName()),
                Map.entry("email", u.getEmail() == null ? "" : u.getEmail()), Map.entry("phone", u.getPhone() == null ? "" : u.getPhone()),
                Map.entry("bio", u.getBio() == null ? "" : u.getBio()),
                Map.entry("shopName", u.getShopName() == null ? "" : u.getShopName()),
                Map.entry("shopDescription", u.getShopDescription() == null ? "" : u.getShopDescription()),
                Map.entry("profileImageUrl", u.getProfileImageUrl() == null ? "" : u.getProfileImageUrl()),
                Map.entry("verified", u.isVerified()), Map.entry("role", u.getRole() == null ? "USER" : u.getRole())
        );
    }

    private String trim(String text, int max) {
        if (text == null) return "";
        String t = text.trim();
        return t.substring(0, Math.min(t.length(), max));
    }
}
