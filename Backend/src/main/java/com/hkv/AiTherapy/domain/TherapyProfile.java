package com.hkv.AiTherapy.domain;

import com.hkv.AiTherapy.encryption.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "therapy_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TherapyProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // 1:1 relationship with User — stored as FK
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** ENCRYPTED — user's preferred display name during sessions */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "preferred_name", columnDefinition = "TEXT")
    private String preferredName;

    /** e.g. '25-34' or what the AI inferred */
    @Column(name = "age_range", length = 50)
    private String ageRange;

    /** 'direct' | 'gentle' | 'reflective' */
    @Column(name = "communication_style", length = 50)
    @Builder.Default
    private String communicationStyle = "gentle";

    /** 'calm' | 'encouraging' | 'analytical' */
    @Column(name = "ai_persona", length = 50)
    @Builder.Default
    private String aiPersona = "calm";

    /** ENCRYPTED — JSON array e.g. '["anxiety","work stress"]' */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "topics_of_concern", columnDefinition = "TEXT")
    private String topicsOfConcern;

    @Column(name = "total_sessions", nullable = false)
    @Builder.Default
    private int totalSessions = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
