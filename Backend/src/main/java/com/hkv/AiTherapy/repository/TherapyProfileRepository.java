package com.hkv.AiTherapy.repository;

import com.hkv.AiTherapy.domain.TherapyProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TherapyProfileRepository extends JpaRepository<TherapyProfile, UUID> {

    Optional<TherapyProfile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    /** Atomically increments total_sessions after a session is completed */
    @Modifying
    @Query("UPDATE TherapyProfile p SET p.totalSessions = p.totalSessions + 1 WHERE p.user.id = :userId")
    void incrementSessionCount(@Param("userId") UUID userId);

    /** Delete the therapy profile for a given user */
    @Modifying
    @Query("DELETE FROM TherapyProfile p WHERE p.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
