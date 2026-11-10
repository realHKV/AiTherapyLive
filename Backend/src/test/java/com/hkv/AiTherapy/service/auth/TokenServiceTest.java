package com.hkv.AiTherapy.service.auth;

import com.hkv.AiTherapy.domain.RefreshToken;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.repository.RefreshTokenRepository;
import com.hkv.AiTherapy.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TokenServiceTest {
    @Test
    void consumedRefreshTokenCannotBeUsedAgain() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        TokenService service = new TokenService(repository, mock(StringRedisTemplate.class));
        User user = User.builder().id(UUID.randomUUID()).isActive(true).build();
        RefreshToken token = RefreshToken.builder().user(user)
                .expiresAt(Instant.now().plusSeconds(300)).build();
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));

        assertSame(token, service.consumeRefreshToken("synthetic-refresh"));
        assertTrue(token.isRevoked());
        assertThrows(IllegalArgumentException.class, () -> service.consumeRefreshToken("synthetic-refresh"));
        verify(repository, times(1)).save(token);
    }

    @Test
    void redisOutageDoesNotRevokeRefreshTokenDuringLogout() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doThrow(new RedisConnectionFailureException("fixture unavailable"))
                .when(values).set(anyString(), anyString(), anyLong(), any());
        TokenService service = new TokenService(repository, redis);
        ReflectionTestUtils.setField(service, "accessTokenExpirySeconds", 900L);
        User user = User.builder().id(UUID.randomUUID()).isActive(true).build();
        RefreshToken token = RefreshToken.builder().user(user)
                .expiresAt(Instant.now().plusSeconds(300)).build();
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        when(jwt.validateToken("synthetic-access")).thenReturn(true);
        when(jwt.getUserIdFromToken("synthetic-access")).thenReturn(user.getId().toString());
        when(jwt.getJti("synthetic-access")).thenReturn("synthetic-jti");

        assertThrows(RedisConnectionFailureException.class,
                () -> service.logout("synthetic-refresh", "synthetic-access", jwt));
        assertFalse(token.isRevoked());
        verify(repository, never()).save(any());
    }
}
