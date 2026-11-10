package com.hkv.AiTherapy.repository;

import com.hkv.AiTherapy.domain.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /** Paginated list of all sessions for a user — newest first */
    Page<Conversation> findByUserIdOrderByStartedAtDesc(UUID userId, Pageable pageable);

    /** All conversations with a given status for background jobs */
    List<Conversation> findByStatus(String status);

    /** Query to find a hanging active session */
    Optional<Conversation> findFirstByUserIdAndStatusOrderByStartedAtDesc(UUID userId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Conversation c WHERE c.id = :id")
    Optional<Conversation> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Finds the most recently completed session for a user.
     * Used by PromptBuilder and session start to provide the last session summary.
     */
    @Query("""
        SELECT c FROM Conversation c
         WHERE c.user.id = :userId
           AND c.status = 'completed'
           AND c.contextEligible = true
           AND c.summaryRevision = c.user.contextRevision
         ORDER BY c.endedAt DESC
         LIMIT 1
        """)
    Optional<Conversation> findLastCompleted(@Param("userId") UUID userId);

    /**
     * Finds sessions that have ended but haven't been summarized yet.
     * Polled by the SummarizationScheduler.
     */
    @Query("""
        SELECT c FROM Conversation c
         WHERE c.status = 'completed'
           AND c.summarizedAt IS NULL
        """)
    List<Conversation> findUnsummarized();

    /** Check if a session belongs to a specific user — used for authorization */
    boolean existsByIdAndUserId(UUID id, UUID userId);

    /** Delete all conversations for a given user — used for account deletion */
    void deleteAllByUserId(UUID userId);

    @Modifying
    @Query(value = "UPDATE conversations SET session_summary = NULL, summary_revision = NULL WHERE user_id = :userId", nativeQuery = true)
    void invalidateSummaries(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "UPDATE conversations SET context_eligible = false, status = CASE WHEN status = 'active' THEN 'abandoned' ELSE status END WHERE user_id = :userId", nativeQuery = true)
    void excludeAllFromContext(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "UPDATE conversations SET context_eligible = false, status = CASE WHEN status = 'active' THEN 'abandoned' ELSE status END WHERE id = :conversationId AND user_id = :userId", nativeQuery = true)
    void excludeFromContext(@Param("userId") UUID userId, @Param("conversationId") UUID conversationId);
}
