package com.hkv.AiTherapy.repository;

import com.hkv.AiTherapy.domain.ExtractionJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ExtractionJobRepository extends JpaRepository<ExtractionJob, UUID> {
    Optional<ExtractionJob> findByIdAndClaimedByAndStatus(UUID id, UUID claimedBy, String status);
    Optional<ExtractionJob> findFirstByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM ExtractionJob j WHERE j.id = :id")
    Optional<ExtractionJob> findByIdForUpdate(@Param("id") UUID id);
}
