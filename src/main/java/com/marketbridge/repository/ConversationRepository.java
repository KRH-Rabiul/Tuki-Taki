package com.marketbridge.repository;

import com.marketbridge.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByBuyerIdAndSellerIdAndProductId(Long buyerId, Long sellerId, Long productId);
    Optional<Conversation> findByBuyerIdAndSellerIdAndProductIdIsNull(Long buyerId, Long sellerId);
    List<Conversation> findByBuyerIdOrSellerIdOrderByUpdatedAtDesc(Long buyerId, Long sellerId);
}
