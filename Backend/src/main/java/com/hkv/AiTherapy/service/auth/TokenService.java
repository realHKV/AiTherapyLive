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
import java.nio.charset.StandardCharsets;
import org.springframework.transaction.annotation.Transactional;
import com.hkv.AiTherapy.security.JwtTokenProvider;

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

    @Transactional
    public RefreshToken consumeRefreshToken(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(hashToken(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if (!token.isValid() || !token.getUser().isActive()) {
            throw new IllegalArgumentException("Refresh token is unavailable");
        }
        token.setRevoked(true);
        refreshTokenRepository.save(token);
        return token;
    }

    /** A failed Redis write leaves the refresh token usable so logout can be retried. */
    @Transactional
    public void logout(String rawRefreshToken, String accessToken, JwtTokenProvider jwtTokenProvider) {
        RefreshToken refresh = null;
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refresh = refreshTokenRepository.findByTokenHashForUpdate(hashToken(rawRefreshToken))
                    .orElse(null);
            if (refresh != null && !refresh.isValid()) {
                refresh = null;
            }
        }
        if (accessToken != null && jwtTokenProvider.validateToken(accessToken)) {
            if (refresh != null && !refresh.getUser().getId().toString().equals(jwtTokenProvider.getUserIdFromToken(accessToken))) {
                throw new IllegalArgumentException("Token owners differ");
            }
            denylistAccessToken(jwtTokenProvider.getJti(accessToken));
        }
        if (refresh != null) {
            refresh.setRevoked(true);
            refreshTokenRepository.save(refresh);
        }
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
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }
}
