package com.hkv.AiTherapy.repository;

import com.hkv.AiTherapy.domain.ChatSessionStart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatSessionStartRepository extends JpaRepository<ChatSessionStart, UUID> {
    Optional<ChatSessionStart> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
