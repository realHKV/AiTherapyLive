package com.hkv.AiTherapy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "extraction_jobs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExtractionJob {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    @Column(nullable = false, length = 30, updatable = false)
    private String purpose;

    @Column(name = "source_revision", nullable = false, updatable = false)
    private long sourceRevision;

    @Column(name = "policy_version", nullable = false, length = 100, updatable = false)
    private String policyVersion;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "replay_count", nullable = false)
    private int replayCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "claimed_by")
    private UUID claimedBy;

    @Column(name = "last_request_id")
    private UUID lastRequestId;

    @Column(name = "last_error_code", length = 50)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
