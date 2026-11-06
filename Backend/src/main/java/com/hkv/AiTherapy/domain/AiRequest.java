package com.hkv.AiTherapy.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_requests", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "idempotency_key"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiRequest {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "idempotency_key", nullable = false, length = 100) private String idempotencyKey;
    @Column(nullable = false, length = 30) private String purpose;
    @Column(nullable = false, length = 10) private String tier;
    @Column(name = "policy_id", nullable = false, length = 100) private String policyId;
    @Column(name = "model_id", nullable = false, length = 255) private String modelId;
    @Column(nullable = false, length = 100) private String region;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "estimated_input_tokens", nullable = false) private int estimatedInputTokens;
    @Column(name = "estimated_output_tokens", nullable = false) private int estimatedOutputTokens;
    @Column(name = "reserved_tokens", nullable = false) private int reservedTokens;
    @Column(name = "actual_input_tokens") private Integer actualInputTokens;
    @Column(name = "actual_output_tokens") private Integer actualOutputTokens;
    @Column(name = "settled_tokens") private Integer settledTokens;
    @Column(name = "provider_attempts", nullable = false) @Builder.Default private int providerAttempts = 0;
    @Column(name = "lease_expires_at") private Instant leaseExpiresAt;
    @Column(name = "failure_code", length = 50) private String failureCode;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "completed_at") private Instant completedAt;
}
