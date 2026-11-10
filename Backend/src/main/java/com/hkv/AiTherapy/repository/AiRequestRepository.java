package com.hkv.AiTherapy.repository;

import com.hkv.AiTherapy.domain.AiRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiRequestRepository extends JpaRepository<AiRequest, UUID> {
    Optional<AiRequest> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM AiRequest r WHERE r.id = :id")
    Optional<AiRequest> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT r FROM AiRequest r WHERE r.status IN ('RESERVED', 'RUNNING') AND r.leaseExpiresAt < :now")
    List<AiRequest> findExpiredLeases(@Param("now") Instant now);
}
