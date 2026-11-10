package com.hkv.AiTherapy.service.usage;

import com.hkv.AiTherapy.service.ai.routing.ResolvedGeneration;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisAdmissionServiceTest {
    private UsagePolicy policy() {
        return new UsagePolicy("usage-test", "test-region", 2, 3, 1000, 2000,
                100, 100000, 50, 50000, 5, 10, 60);
    }

    @Test
    void rejectedAndUnavailableRedisDoNotPermitGeneration() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString())).thenReturn(-3000L);
        RedisAdmissionService service = new RedisAdmissionService(redis, policy());
        var limited = assertThrows(RedisAdmissionService.RateLimitedException.class,
                () -> service.admit(UUID.randomUUID(), ResolvedGeneration.Tier.FREE));
        assertEquals(3, limited.retryAfterSeconds());

        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("synthetic redis outage"));
        assertEquals(UsageException.Code.ACCOUNTING_UNAVAILABLE,
                assertThrows(UsageException.class, () -> service.admit(UUID.randomUUID(), ResolvedGeneration.Tier.FREE)).code());
    }
}
