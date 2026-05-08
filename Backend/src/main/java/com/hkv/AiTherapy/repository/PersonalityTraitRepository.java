package com.hkv.AiTherapy.repository;

import com.hkv.AiTherapy.domain.PersonalityTrait;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PersonalityTraitRepository extends JpaRepository<PersonalityTrait, UUID> {

    List<PersonalityTrait> findByUserIdOrderByConfidenceDesc(UUID userId);

    /**
     * Returns the top N traits for a user, ordered by confidence (highest first).
     * Used by PromptBuilder to inject top 5 traits into the AI system prompt.
     */
    @Query("""
        SELECT t FROM PersonalityTrait t
         WHERE t.user.id = :userId
         ORDER BY t.confidence DESC
         LIMIT :limit
        """)
    List<PersonalityTrait> findTopByUserId(@Param("userId") UUID userId,
                                           @Param("limit") int limit);

    Optional<PersonalityTrait> findByUserIdAndTraitKey(UUID userId, String traitKey);

    void deleteByUserIdAndTraitKey(UUID userId, String traitKey);

    /** Delete all personality traits for a given user */
    void deleteAllByUserId(UUID userId);
}
