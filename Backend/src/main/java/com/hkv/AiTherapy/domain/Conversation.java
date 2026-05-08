package com.hkv.AiTherapy.domain;

import com.hkv.AiTherapy.encryption.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 'active' | 'completed' | 'abandoned' */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "active";

    /** ENCRYPTED — AI-generated summary produced after the session ends */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "session_summary", columnDefinition = "TEXT")
    private String sessionSummary;

    /** 'anxious' | 'sad' | 'neutral' | 'hopeful' */
    @Column(name = "mood_start", length = 30)
    private String moodStart;

    @Column(name = "mood_end", length = 30)
    private String moodEnd;

    /** Running total of tokens used in this session (cost tracking) */
    @Column(name = "token_count", nullable = false)
    @Builder.Default
    private int tokenCount = 0;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    /** Set once the async summarization scheduler has finished */
    @Column(name = "summarized_at")
    private Instant summarizedAt;
}
