package com.hkv.AiTherapy.service.usage;

import com.hkv.AiTherapy.service.ai.routing.ResolvedGeneration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Atomic short-window admission and one-user lease. Durable accounting remains in PostgreSQL. */
@Service
public class RedisAdmissionService {
    private static final DefaultRedisScript<Long> WINDOW = new DefaultRedisScript<>("""
        local count = redis.call('INCR', KEYS[1])
        if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
        if count > tonumber(ARGV[2]) then return -redis.call('PTTL', KEYS[1]) end
        return redis.call('PTTL', KEYS[1])
        """, Long.class);
    private static final DefaultRedisScript<Long> ACQUIRE_LEASE = new DefaultRedisScript<>("""
        if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2]) then return 1 end
        return 0
        """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_LEASE = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
        return 0
        """, Long.class);
    private static final DefaultRedisScript<Long> RENEW_LEASE = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
        return 0
        """, Long.class);
    private final StringRedisTemplate redis;
    private final UsagePolicy policy;

    public RedisAdmissionService(StringRedisTemplate redis, UsagePolicy policy) { this.redis = redis; this.policy = policy; }

    /** @return seconds until reset; throws when rejected or unavailable. */
    public long admit(UUID userId, ResolvedGeneration.Tier tier) {
        try {
            Long response = redis.execute(WINDOW, List.of("ai:window:" + userId),
                    Long.toString(Duration.ofMinutes(1).toMillis()), Integer.toString(policy.minuteRequests(tier)));
            if (response == null) throw new UsageException(UsageException.Code.ACCOUNTING_UNAVAILABLE);
            if (response < 0) throw new RateLimitedException(Math.max(1, (-response + 999) / 1000));
            return Math.max(1, (response + 999) / 1000);
        } catch (UsageException e) { throw e; }
        catch (DataAccessException e) { throw new UsageException(UsageException.Code.ACCOUNTING_UNAVAILABLE); }
    }
    public boolean acquireLease(UUID userId, UUID requestId) {
        try {
            Long result = redis.execute(ACQUIRE_LEASE, List.of("ai:lease:" + userId), requestId.toString(),
                    Long.toString(policy.leaseDuration().toMillis()));
            if (result == null) throw new UsageException(UsageException.Code.ACCOUNTING_UNAVAILABLE);
            return result == 1;
        } catch (DataAccessException e) { throw new UsageException(UsageException.Code.ACCOUNTING_UNAVAILABLE); }
    }
    public void releaseLease(UUID userId, UUID requestId) {
        try { redis.execute(RELEASE_LEASE, List.of("ai:lease:" + userId), requestId.toString()); }
        catch (DataAccessException e) { throw new UsageException(UsageException.Code.ACCOUNTING_UNAVAILABLE); }
    }
    public boolean renewLease(UUID userId, UUID requestId) {
        try {
            Long result = redis.execute(RENEW_LEASE, List.of("ai:lease:" + userId), requestId.toString(),
                    Long.toString(policy.leaseDuration().toMillis()));
            if (result == null) throw new UsageException(UsageException.Code.ACCOUNTING_UNAVAILABLE);
            return result == 1;
        } catch (DataAccessException e) { throw new UsageException(UsageException.Code.ACCOUNTING_UNAVAILABLE); }
    }

    public static final class RateLimitedException extends RuntimeException {
        private final long retryAfterSeconds;
        public RateLimitedException(long retryAfterSeconds) { this.retryAfterSeconds = retryAfterSeconds; }
        public long retryAfterSeconds() { return retryAfterSeconds; }
    }
}
