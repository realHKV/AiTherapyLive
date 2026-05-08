package com.hkv.AiTherapy.domain;

import com.hkv.AiTherapy.encryption.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "long_term_memories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LongTermMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The conversation from which this memory was extracted — may be null if manually added */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_conv_id")
    private Conversation sourceConversation;

    /** 'life_event' | 'goal' | 'relationship' | 'preference' */
    @Column(name = "memory_type", nullable = false, length = 50)
    private String memoryType;

    /** ENCRYPTED — short label e.g. "Sister's wedding next month" */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    /** ENCRYPTED — full context / detail */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    /** 1–10; higher = more important; used to rank what gets injected into prompt */
    @Column(name = "importance", nullable = false)
    @Builder.Default
    private int importance = 5;

    /** When the event occurred / is expected to occur */
    @Column(name = "occurred_at")
    private LocalDate occurredAt;

    /** When the AI should proactively follow up on this memory */
    @Column(name = "follow_up_at")
    private LocalDate followUpAt;

    @Column(name = "is_resolved", nullable = false)
    @Builder.Default
    private boolean isResolved = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
