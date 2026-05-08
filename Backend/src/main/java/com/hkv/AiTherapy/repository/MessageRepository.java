package com.hkv.AiTherapy.repository;

import com.hkv.AiTherapy.domain.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /** All messages in a conversation ordered chronologically — used by summarization job */
    List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    /** Used by ChatService to get the last N messages for AI context */
    List<Message> findByConversationId(UUID conversationId, Pageable pageable);

    /**
     * Cursor-based pagination: messages after a given message ID.
     * Used by GET /chat/sessions/{id}/messages for efficient scrolling.
     */
    @Query("""
        SELECT m FROM Message m
         WHERE m.conversation.id = :conversationId
           AND m.createdAt > (SELECT m2.createdAt FROM Message m2 WHERE m2.id = :cursorId)
         ORDER BY m.createdAt ASC
        """)
    List<Message> findAfterCursor(@Param("conversationId") UUID conversationId,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    /** Count messages in a session — used when building the session-end response */
    long countByConversationId(UUID conversationId);

    /** Delete all messages older than N days — used by the periodic cleanup job */
    @Query("""
        DELETE FROM Message m
         WHERE m.conversation.id IN (
             SELECT c.id FROM Conversation c WHERE c.user.id = :userId
         )
           AND m.createdAt < (CURRENT_TIMESTAMP - :days day)
        """)
    void deleteOldMessagesByUserId(@Param("userId") UUID userId,
                                   @Param("days") int days);

    /** Delete ALL messages for ALL conversations belonging to a user — used for account deletion */
    @Modifying
    @Query("""
        DELETE FROM Message m
         WHERE m.conversation.id IN (
             SELECT c.id FROM Conversation c WHERE c.user.id = :userId
         )
        """)
    void deleteAllByUserId(@Param("userId") UUID userId);
}
