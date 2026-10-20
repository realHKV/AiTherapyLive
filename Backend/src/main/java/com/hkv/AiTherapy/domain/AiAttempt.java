package com.hkv.AiTherapy.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_attempts", uniqueConstraints = @UniqueConstraint(columnNames = {"request_id", "attempt_number"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiAttempt {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "request_id", nullable = false) private AiRequest request;
    @Column(name = "attempt_number", nullable = false) private int attemptNumber;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "actual_input_tokens") private Integer actualInputTokens;
    @Column(name = "actual_output_tokens") private Integer actualOutputTokens;
    @Column(name = "failure_code", length = 50) private String failureCode;
    @CreationTimestamp @Column(name = "started_at", nullable = false, updatable = false) private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
}
