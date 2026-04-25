package com.hkv.AiTherapy.repository;

import com.hkv.AiTherapy.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Look up a refresh token by its SHA-256 hash to validate it */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revoke all tokens for a user — used on logout-all-devices */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.user.id = :userId")
    void revokeAllByUserId(@Param("userId") UUID userId);

    /** Delete physically expired tokens — called by a periodic cleanup job */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now")
    void deleteExpiredTokens(@Param("now") Instant now);

    /** Count active (non-revoked, non-expired) sessions for a user */
    @Query("""
        SELECT COUNT(t) FROM RefreshToken t
         WHERE t.user.id = :userId
           AND t.revoked = false
           AND t.expiresAt > :now
        """)
    long countActiveSessions(@Param("userId") UUID userId,
                             @Param("now") Instant now);

    /** Delete all refresh tokens for a given user — used for account deletion */
    void deleteAllByUserId(UUID userId);
}
