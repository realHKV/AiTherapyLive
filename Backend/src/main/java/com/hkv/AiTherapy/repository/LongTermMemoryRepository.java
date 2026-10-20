package com.hkv.AiTherapy.repository;

import com.hkv.AiTherapy.domain.LongTermMemory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LongTermMemoryRepository extends JpaRepository<LongTermMemory, UUID> {

    /**
     * Returns the top N memories for a user, ordered by importance (highest first).
     * Used by PromptBuilder to inject context into the AI system prompt.
     */
    @Query("""
        SELECT m FROM LongTermMemory m LEFT JOIN m.sourceConversation sc
         WHERE m.user.id = :userId
           AND m.status = 'active'
           AND (m.expiresAt IS NULL OR m.expiresAt > CURRENT_TIMESTAMP)
           AND (sc.id IS NULL OR sc.contextEligible = true)
         ORDER BY m.importance DESC
         LIMIT :limit
        """)
    List<LongTermMemory> findTopByImportance(@Param("userId") UUID userId,
                                             @Param("limit") int limit);

    /**
     * Memories with a follow-up date due today or overdue.
     * Injected as "ask about this today" items in the AI system prompt.
     */
    @Query("""
        SELECT m FROM LongTermMemory m LEFT JOIN m.sourceConversation sc
         WHERE m.user.id = :userId
           AND m.status = 'active'
           AND (m.expiresAt IS NULL OR m.expiresAt > CURRENT_TIMESTAMP)
           AND (sc.id IS NULL OR sc.contextEligible = true)
           AND m.followUpAt IS NOT NULL
           AND m.followUpAt <= :today
         ORDER BY m.importance DESC
        """)
    List<LongTermMemory> findDueFollowUps(@Param("userId") UUID userId,
                                          @Param("today") LocalDate today);

    /** Paginated list for the GET /profile/me/memories endpoint with optional type filter */
    @Query("""
        SELECT m FROM LongTermMemory m
         WHERE m.user.id = :userId
           AND m.status <> 'pending'
           AND m.confirmedAt IS NOT NULL
           AND (:type IS NULL OR m.memoryType = :type)
           AND (:resolved IS NULL OR m.isResolved = :resolved)
         ORDER BY m.importance DESC, m.createdAt DESC
        """)
    Page<LongTermMemory> findByUserIdFiltered(@Param("userId") UUID userId,
                                              @Param("type") String type,
                                              @Param("resolved") Boolean resolved,
                                              Pageable pageable);

    /** Ownership check — used before allowing delete */
    boolean existsByIdAndUserId(UUID id, UUID userId);

    /** Find all memories for a user (useful for in-memory decryption filtering) */
    List<LongTermMemory> findByUserId(UUID userId);

    @Query("""
        SELECT m FROM LongTermMemory m LEFT JOIN m.sourceConversation sc
         WHERE m.user.id = :userId AND m.status = :status
           AND m.sourceRevision = m.user.contextRevision
           AND (sc.id IS NULL OR sc.contextEligible = true)
         ORDER BY m.createdAt DESC
        """)
    Page<LongTermMemory> findByUserIdAndStatusOrderByCreatedAtDesc(@Param("userId") UUID userId,
            @Param("status") String status, Pageable pageable);

    java.util.Optional<LongTermMemory> findByIdAndUserId(UUID id, UUID userId);

    boolean existsBySourceOutputId(UUID sourceOutputId);

    List<LongTermMemory> findByUserIdAndSourceConversationId(UUID userId, UUID sourceConversationId);

    /** Delete all long-term memories for a given user */
    void deleteAllByUserId(UUID userId);
}
