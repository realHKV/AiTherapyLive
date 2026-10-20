package com.hkv.AiTherapy.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ai_usage_buckets", uniqueConstraints = @UniqueConstraint(columnNames = {"scope", "scope_key", "window_start"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiUsageBucket {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 30) private String scope;
    @Column(name = "scope_key", nullable = false, length = 400) private String scopeKey;
    @Column(name = "window_start", nullable = false) private LocalDate windowStart;
    @Column(name = "used_requests", nullable = false) @Builder.Default private int usedRequests = 0;
    @Column(name = "reserved_requests", nullable = false) @Builder.Default private int reservedRequests = 0;
    @Column(name = "used_tokens", nullable = false) @Builder.Default private int usedTokens = 0;
    @Column(name = "reserved_tokens", nullable = false) @Builder.Default private int reservedTokens = 0;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
