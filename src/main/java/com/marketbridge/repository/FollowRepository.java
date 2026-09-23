package com.marketbridge.repository;

import com.marketbridge.model.Follow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    Optional<Follow> findByFollowerIdAndSellerId(Long followerId, Long sellerId);
    List<Follow> findByFollowerId(Long followerId);
    List<Follow> findBySellerId(Long sellerId);
    long countBySellerId(Long sellerId);
}
