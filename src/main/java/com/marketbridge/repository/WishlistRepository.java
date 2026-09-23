package com.marketbridge.repository;

import com.marketbridge.model.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    List<Wishlist> findByBuyerId(Long buyerId);
    Optional<Wishlist> findByBuyerIdAndProductId(Long buyerId, Long productId);

    // Written as an explicit bulk DELETE (with @Modifying + @Transactional)
    // rather than relying on the derived "deleteBy..." convention, so this
    // reliably removes the row in one statement.
    @Modifying
    @Transactional
    @Query("DELETE FROM Wishlist w WHERE w.buyerId = :buyerId AND w.productId = :productId")
    void deleteByBuyerIdAndProductId(Long buyerId, Long productId);
}
