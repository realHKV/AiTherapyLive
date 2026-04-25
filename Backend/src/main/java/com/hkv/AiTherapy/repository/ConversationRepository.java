package com.hkv.AiTherapy.repository;

import com.hkv.AiTherapy.domain.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Finds the most recently completed session for a user.
     * Used by PromptBuilder and session start to provide the last session summary.
     */
    @Query("""
        SELECT c FROM Conversation c
         WHERE c.user.id = :userId
           AND c.status = 'completed'
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
}
