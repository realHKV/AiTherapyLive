package com.hkv.AiTherapy.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores subscription tier per user.
 * Separate from User to keep the users table lightweight.
 */
@Entity
@Table(name = "user_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** One-to-one with the users table. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * 'FREE' or 'PRO'.
     * Note: kept as a plain String (not enum) for flexibility.
     */
    @Column(name = "tier", nullable = false, length = 10)
    @Builder.Default
    private String tier = "FREE";

    /** Only set when tier = 'PRO'. Null for free users. */
    @Column(name = "pro_expires_at")
    private Instant proExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ─── Convenience helpers ────────────────────────────────────────────────

    public boolean isPro() {
        return "PRO".equals(tier) && proExpiresAt != null && proExpiresAt.isAfter(Instant.now());
    }
}
