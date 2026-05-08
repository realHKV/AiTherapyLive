package com.hkv.AiTherapy.domain;

import com.hkv.AiTherapy.encryption.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "personality_traits",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "trait_key"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalityTrait {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** e.g. 'perfectionist', 'introvert' */
    @Column(name = "trait_key", nullable = false, length = 100)
    private String traitKey;

    /** ENCRYPTED — AI-inferred description of the trait */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "trait_value", columnDefinition = "TEXT")
    private String traitValue;

    /** 0.0 – 1.0 confidence score */
    @Column(name = "confidence", nullable = false)
    @Builder.Default
    private double confidence = 0.5;

    /** 'user_stated' | 'ai_inferred' */
    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
