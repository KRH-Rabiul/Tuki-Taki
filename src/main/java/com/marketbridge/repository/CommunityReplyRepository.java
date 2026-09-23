package com.marketbridge.repository;

import com.marketbridge.model.CommunityReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityReplyRepository extends JpaRepository<CommunityReply, Long> {
    List<CommunityReply> findByPostIdOrderByCreatedAtAsc(Long postId);
}
