package com.marketbridge.controller;

import com.marketbridge.model.Order;
import com.marketbridge.model.Review;
import com.marketbridge.model.User;
import com.marketbridge.repository.OrderRepository;
import com.marketbridge.repository.ReviewRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private OrderRepository orderRepository;

    // Anyone can read a product's reviews - that's public browsing info.
    @GetMapping("/product/{productId}")
    public ResponseEntity<?> getReviewsForProduct(@PathVariable Long productId) {
        List<Review> reviews = reviewRepository.findByProductId(productId);
        double average = reviews.stream().mapToInt(Review::getRating).average().orElse(0);
        return ResponseEntity.ok(Map.of(
                "reviews", reviews,
                "average", Math.round(average * 10.0) / 10.0,
                "count", reviews.size()
        ));
    }

    // A marketplace member leaves one review per product after collection.
    @PostMapping
    public ResponseEntity<?> addReview(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null || "ADMIN".equals(currentUser.getRole())) {
            return ResponseEntity.status(403).body(Map.of("message", "Log in with a marketplace account to leave reviews"));
        }

        Long productId = Long.valueOf(body.get("productId").toString());
        int rating = Integer.parseInt(body.get("rating").toString());
        String comment = body.get("comment") == null ? "" : body.get("comment").toString();

        if (rating < 1 || rating > 5) {
            return ResponseEntity.badRequest().body(Map.of("message", "Rating must be between 1 and 5"));
        }
        if (comment.length() > 1000) {
            return ResponseEntity.badRequest().body(Map.of("message", "Review is too long (1000 characters max)"));
        }

        // Reviews are allowed only after the buyer confirms collection.
        boolean hasDeliveredOrder = orderRepository.findByBuyerId(currentUser.getId()).stream()
                .anyMatch(o -> o.getProductId().equals(productId) && "COLLECTED".equals(o.getStatus()));
        if (!hasDeliveredOrder) {
            return ResponseEntity.status(403).body(Map.of("message", "You can only review products you've received"));
        }

        Optional<Review> existing = reviewRepository.findByProductIdAndBuyerId(productId, currentUser.getId());
        Review review = existing.orElse(new Review());
        review.setProductId(productId);
        review.setBuyerId(currentUser.getId());
        review.setBuyerName(currentUser.getName());
        review.setRating(rating);
        review.setComment(comment);
        review.setCreatedAt(LocalDateTime.now());

        return ResponseEntity.ok(reviewRepository.save(review));
    }
}
