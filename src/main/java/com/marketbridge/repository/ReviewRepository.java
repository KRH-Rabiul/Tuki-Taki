package com.marketbridge.repository;

import com.marketbridge.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProductId(Long productId);
    List<Review> findByProductIdIn(List<Long> productIds);
    Optional<Review> findByProductIdAndBuyerId(Long productId, Long buyerId);
}
