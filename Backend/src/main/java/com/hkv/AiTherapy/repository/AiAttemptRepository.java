package com.hkv.AiTherapy.repository;

import com.hkv.AiTherapy.domain.AiAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiAttemptRepository extends JpaRepository<AiAttempt, UUID> {
    List<AiAttempt> findByRequestIdAndStatus(UUID requestId, String status);
}
