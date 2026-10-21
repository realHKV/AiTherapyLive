package com.hkv.AiTherapy.repository;

import com.hkv.AiTherapy.domain.ChatTurn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ChatTurnRepository extends JpaRepository<ChatTurn, UUID> {
    Optional<ChatTurn> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
    Optional<ChatTurn> findByRequestIdAndUserId(UUID requestId, UUID userId);
    List<ChatTurn> findByUserIdAndConversationIdAndStatus(UUID userId, UUID conversationId, String status);
    Optional<ChatTurn> findFirstByConversationIdAndPurposeAndStatusOrderByCreatedAtDesc(
            UUID conversationId, String purpose, String status);
}
