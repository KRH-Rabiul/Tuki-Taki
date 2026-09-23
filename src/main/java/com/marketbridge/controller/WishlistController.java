package com.marketbridge.controller;

import com.marketbridge.model.Product;
import com.marketbridge.model.User;
import com.marketbridge.model.Wishlist;
import com.marketbridge.repository.ProductRepository;
import com.marketbridge.repository.WishlistRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private ProductRepository productRepository;

    // Returns the buyer's wishlisted products (full product info, not just IDs).
    @GetMapping
    public ResponseEntity<?> getWishlist(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null) {
            return ResponseEntity.status(403).body(Map.of("message", "Login required"));
        }
        List<Long> productIds = wishlistRepository.findByBuyerId(currentUser.getId()).stream()
                .map(Wishlist::getProductId).collect(Collectors.toList());
        List<Product> products = productRepository.findAllById(productIds);
        return ResponseEntity.ok(products);
    }

    @PostMapping("/{productId}")
    public ResponseEntity<?> addToWishlist(HttpServletRequest request, @PathVariable Long productId) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null || "ADMIN".equals(currentUser.getRole())) {
            return ResponseEntity.status(403).body(Map.of("message", "Log in with a marketplace account to use wishlist"));
        }

        Optional<Wishlist> existing = wishlistRepository.findByBuyerIdAndProductId(currentUser.getId(), productId);
        if (existing.isPresent()) {
            return ResponseEntity.ok(Map.of("message", "Already in wishlist"));
        }

        Wishlist entry = new Wishlist();
        entry.setBuyerId(currentUser.getId());
        entry.setProductId(productId);
        wishlistRepository.save(entry);
        return ResponseEntity.ok(Map.of("message", "Added to wishlist"));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<?> removeFromWishlist(HttpServletRequest request, @PathVariable Long productId) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null) {
            return ResponseEntity.status(403).body(Map.of("message", "Login required"));
        }
        wishlistRepository.deleteByBuyerIdAndProductId(currentUser.getId(), productId);
        return ResponseEntity.ok(Map.of("message", "Removed from wishlist"));
    }
}
