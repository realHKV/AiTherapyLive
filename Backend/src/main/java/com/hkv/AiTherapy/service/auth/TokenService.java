package com.hkv.AiTherapy.service.auth;

import com.hkv.AiTherapy.domain.RefreshToken;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpirySeconds;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpirySeconds;

    public TokenService(RefreshTokenRepository refreshTokenRepository, StringRedisTemplate redisTemplate) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Generates a raw refresh token string, hashes it, saves the hash in DB, and returns the raw string.
     */
    public String createRefreshToken(User user, String deviceInfo) {
        String rawToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .deviceInfo(deviceInfo)
                .expiresAt(Instant.now().plusSeconds(refreshTokenExpirySeconds))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    /**
     * Validates a raw refresh token against the DB hash.
     * Returns the RefreshToken entity if valid, otherwise throws RuntimeException.
     */
    public RefreshToken validateAndGetRefreshToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (!token.isValid()) {
            throw new RuntimeException("Refresh token is expired or revoked");
        }
        return token;
    }

    /**
     * Revokes a specific refresh token.
     */
    public void revokeRefreshToken(RefreshToken token) {
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }

    /**
     * Adds a JWT ID (jti) to the Redis denylist for the remainder of its lifetime.
     */
    public void denylistAccessToken(String jti) {
        // In a perfect system, we'd extract the actual remaining expiry from the token itself.
        // Here we just use the max expiry for safety.
        redisTemplate.opsForValue().set("jwt:denylist:" + jti, "true", accessTokenExpirySeconds, TimeUnit.SECONDS);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }
}
