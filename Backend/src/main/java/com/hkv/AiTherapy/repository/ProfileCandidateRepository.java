package com.hkv.AiTherapy.repository;

import com.hkv.AiTherapy.domain.ProfileCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileCandidateRepository extends JpaRepository<ProfileCandidate, UUID> {
    long countByUserIdAndStatus(UUID userId, String status);
    @Query("SELECT candidate FROM ProfileCandidate candidate JOIN candidate.user owner "
            + "JOIN candidate.conversation conversation WHERE owner.id = :userId "
            + "AND candidate.status = 'pending' AND candidate.sourceRevision = owner.contextRevision "
            + "AND conversation.contextEligible = true ORDER BY candidate.createdAt DESC")
    List<ProfileCandidate> findPendingCurrent(@Param("userId") UUID userId, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT candidate FROM ProfileCandidate candidate WHERE candidate.id = :id AND candidate.user.id = :userId")
    Optional<ProfileCandidate> findOwnedForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);
}
