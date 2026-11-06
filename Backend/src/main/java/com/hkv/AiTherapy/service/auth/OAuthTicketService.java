package com.hkv.AiTherapy.service.auth;

import com.hkv.AiTherapy.dto.response.AuthResponse;
import com.hkv.AiTherapy.repository.UserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Service
public class OAuthTicketService {
    private static final Duration LIFETIME = Duration.ofSeconds(60);
    private final SecureRandom random = new SecureRandom();
    private final StringRedisTemplate redis;
    private final UserRepository users;
    private final AuthService authService;

    public OAuthTicketService(StringRedisTemplate redis, UserRepository users, AuthService authService) {
        this.redis = redis;
        this.users = users;
        this.authService = authService;
    }

    public String issue(UUID userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set("oauth:ticket:" + ticket, userId.toString(), LIFETIME);
        return ticket;
    }

    @Transactional
    public AuthResponse exchange(String ticket, String deviceInfo) {
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("Invalid ticket");
        }
        String userId = redis.opsForValue().getAndDelete("oauth:ticket:" + ticket);
        if (userId == null) {
            throw new IllegalArgumentException("Ticket expired or consumed");
        }
        var user = users.findById(UUID.fromString(userId))
                .filter(u -> u.isActive())
                .orElseThrow(() -> new IllegalArgumentException("Account unavailable"));
        return authService.issueForUser(user, deviceInfo);
    }
}
