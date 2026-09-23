package com.marketbridge.repository;

import com.marketbridge.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
    long countByReceiverIdAndReadStatusFalse(Long receiverId);
}
