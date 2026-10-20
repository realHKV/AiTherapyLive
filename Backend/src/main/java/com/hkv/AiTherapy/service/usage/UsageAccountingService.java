package com.hkv.AiTherapy.service.usage;

import com.hkv.AiTherapy.domain.AiAttempt;
import com.hkv.AiTherapy.domain.AiRequest;
import com.hkv.AiTherapy.repository.AiAttemptRepository;
import com.hkv.AiTherapy.repository.AiRequestRepository;
import com.hkv.AiTherapy.service.ai.routing.ResolvedGeneration;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** PostgreSQL is the source of truth; Redis is only a short-lived admission and lease layer. */
@Service
public class UsageAccountingService {
    private static final String USER = "USER_DAILY", ACCOUNT = "ACCOUNT_DAILY", MODEL = "MODEL_REGION_DAILY";
    private final JdbcTemplate jdbc;
    private final AiRequestRepository requests;
    private final AiAttemptRepository attempts;
    private final UsagePolicy policy;
    private final Clock clock;

    public UsageAccountingService(JdbcTemplate jdbc, AiRequestRepository requests, AiAttemptRepository attempts,
                                  UsagePolicy policy, Clock clock) {
        this.jdbc = jdbc; this.requests = requests; this.attempts = attempts; this.policy = policy; this.clock = clock;
    }

    @Transactional
    public UsageReservation reserve(ResolvedGeneration generation, String idempotencyKey,
                                    int estimatedInputTokens) {
        validateKey(idempotencyKey);
        if (estimatedInputTokens < 0) throw new IllegalArgumentException("Estimated input tokens cannot be negative");
        int reserved = Math.addExact(estimatedInputTokens, generation.maxOutputTokens());
        var existing = requests.findByUserIdAndIdempotencyKey(generation.userId(), idempotencyKey);
        if (existing.isPresent()) {
            verifySameRequest(existing.get(), generation, reserved);
            return new UsageReservation(existing.get().getId(), true, existing.get().getLeaseExpiresAt());
        }
        Instant now = clock.instant();
        Instant lease = now.plus(policy.leaseDuration());
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
            INSERT INTO ai_requests (id,user_id,idempotency_key,purpose,tier,policy_id,model_id,region,status,
             estimated_input_tokens,estimated_output_tokens,reserved_tokens,lease_expires_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (user_id,idempotency_key) DO NOTHING
            """, id, generation.userId(), idempotencyKey, generation.purpose().name(), generation.tier().name(),
                generation.policyId(), generation.modelId(), policy.region(), "RESERVED", estimatedInputTokens,
                generation.maxOutputTokens(), reserved, Timestamp.from(lease));
        if (inserted == 0) {
            AiRequest raced = requests.findByUserIdAndIdempotencyKey(generation.userId(), idempotencyKey)
                    .orElseThrow(() -> new UsageException(UsageException.Code.ACCOUNTING_UNAVAILABLE));
            verifySameRequest(raced, generation, reserved);
            return new UsageReservation(raced.getId(), true, raced.getLeaseExpiresAt());
        }
        LocalDate day = LocalDate.ofInstant(now, ZoneOffset.UTC);
        try {
            if (!reserveBucket(USER, generation.userId().toString(), day, policy.userRequests(generation.tier()), policy.userTokens(generation.tier()), reserved))
                throw new UsageException(UsageException.Code.USER_LIMIT);
            if (!reserveBucket(ACCOUNT, "global", day, policy.accountRequests(), policy.accountTokens(), reserved)
                    || !reserveBucket(MODEL, generation.modelId() + "|" + policy.region(), day, policy.modelRequests(), policy.modelTokens(), reserved))
                throw new UsageException(UsageException.Code.CAPACITY_LIMIT);
        } catch (DataAccessException e) {
            throw new UsageException(UsageException.Code.ACCOUNTING_UNAVAILABLE);
        }
        return new UsageReservation(id, false, lease);
    }

    @Transactional
    public void startAttempt(UUID requestId) {
        AiRequest request = locked(requestId);
        if (!"RESERVED".equals(request.getStatus()) && !"RUNNING".equals(request.getStatus())) return;
        request.setStatus("RUNNING"); request.setProviderAttempts(request.getProviderAttempts() + 1);
        request.setLeaseExpiresAt(clock.instant().plus(policy.leaseDuration()));
        attempts.save(AiAttempt.builder().id(UUID.randomUUID()).request(request)
                .attemptNumber(request.getProviderAttempts()).status("RUNNING").build());
    }

    @Transactional
    public void settle(UUID requestId, Integer actualInputTokens, Integer actualOutputTokens,
                       String terminalStatus, String failureCode) {
        AiRequest request = locked(requestId);
        if (isTerminal(request.getStatus())) return;
        if (!List.of("COMPLETED", "FAILED", "CANCELLED").contains(terminalStatus)) throw new IllegalArgumentException("Invalid terminal status");
        int settled = actualInputTokens == null || actualOutputTokens == null ? request.getReservedTokens()
                : Math.addExact(nonnegative(actualInputTokens), nonnegative(actualOutputTokens));
        // A provider exceeding its reserved maximum is still recorded and charged; it is a policy/configuration alert.
        request.setActualInputTokens(actualInputTokens); request.setActualOutputTokens(actualOutputTokens);
        request.setSettledTokens(settled); request.setStatus(terminalStatus); request.setFailureCode(failureCode);
        request.setLeaseExpiresAt(null); request.setCompletedAt(clock.instant());
        for (AiAttempt attempt : attempts.findByRequestIdAndStatus(requestId, "RUNNING")) {
            attempt.setStatus(terminalStatus);
            attempt.setActualInputTokens(actualInputTokens);
            attempt.setActualOutputTokens(actualOutputTokens);
            attempt.setFailureCode(failureCode);
            attempt.setCompletedAt(clock.instant());
        }
        LocalDate day = LocalDate.ofInstant(request.getCreatedAt(), ZoneOffset.UTC);
        settleBuckets(request, day, settled);
    }

    @Transactional
    public int reconcileExpiredReservations() {
        int reconciled = 0;
        for (AiRequest request : requests.findExpiredLeases(clock.instant())) {
            AiRequest locked = locked(request.getId());
            if (isTerminal(locked.getStatus()) || locked.getLeaseExpiresAt() == null || !locked.getLeaseExpiresAt().isBefore(clock.instant())) continue;
            if ("RUNNING".equals(locked.getStatus())) settle(locked.getId(), null, null, "FAILED", "LEASE_EXPIRED");
            else {
                releaseBuckets(locked, LocalDate.ofInstant(locked.getCreatedAt(), ZoneOffset.UTC));
                locked.setStatus("EXPIRED"); locked.setFailureCode("LEASE_EXPIRED"); locked.setLeaseExpiresAt(null); locked.setCompletedAt(clock.instant());
            }
            reconciled++;
        }
        return reconciled;
    }

    @Transactional(readOnly = true)
    public UsageStatus status(UUID userId, ResolvedGeneration.Tier tier) {
        LocalDate day = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        Map<String, Object> bucket;
        try {
            bucket = jdbc.queryForMap("""
                SELECT COALESCE(used_requests,0) used_requests, COALESCE(reserved_requests,0) reserved_requests,
                       COALESCE(used_tokens,0) used_tokens, COALESCE(reserved_tokens,0) reserved_tokens
                FROM ai_usage_buckets WHERE scope = ? AND scope_key = ? AND window_start = ?
                """, USER, userId.toString(), day);
        } catch (EmptyResultDataAccessException ignored) {
            bucket = Map.of();
        }
        int requestsUsed = intValue(bucket, "used_requests") + intValue(bucket, "reserved_requests");
        int tokensUsed = intValue(bucket, "used_tokens") + intValue(bucket, "reserved_tokens");
        Instant reset = day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return new UsageStatus(tier, policy.policyId(), Math.max(0, policy.userRequests(tier) - requestsUsed),
                Math.max(0, policy.userTokens(tier) - tokensUsed), intValue(bucket, "reserved_tokens"), reset);
    }

    private boolean reserveBucket(String scope, String key, LocalDate day, int requestLimit, int tokenLimit, int tokens) {
        return jdbc.update("""
            INSERT INTO ai_usage_buckets (id,scope,scope_key,window_start,reserved_requests,reserved_tokens,updated_at)
            SELECT gen_random_uuid(),?,?,?,1,?,NOW() WHERE 1 <= ? AND ? <= ?
            ON CONFLICT (scope,scope_key,window_start) DO UPDATE SET
              reserved_requests = ai_usage_buckets.reserved_requests + 1,
              reserved_tokens = ai_usage_buckets.reserved_tokens + EXCLUDED.reserved_tokens, updated_at = NOW()
            WHERE ai_usage_buckets.used_requests + ai_usage_buckets.reserved_requests + 1 <= ?
              AND ai_usage_buckets.used_tokens + ai_usage_buckets.reserved_tokens + EXCLUDED.reserved_tokens <= ?
            """, scope, key, day, tokens, requestLimit, tokens, tokenLimit, requestLimit, tokenLimit) == 1;
    }

    private void settleBuckets(AiRequest request, LocalDate day, int tokens) {
        for (BucketRef bucket : buckets(request, day)) jdbc.update("""
            UPDATE ai_usage_buckets SET reserved_requests = GREATEST(0,reserved_requests-1),
              reserved_tokens = GREATEST(0,reserved_tokens-?), used_requests = used_requests+1,
              used_tokens = used_tokens+?, updated_at=NOW() WHERE scope=? AND scope_key=? AND window_start=?
            """, request.getReservedTokens(), tokens, bucket.scope, bucket.key, day);
    }
    private void releaseBuckets(AiRequest request, LocalDate day) {
        for (BucketRef bucket : buckets(request, day)) jdbc.update("""
            UPDATE ai_usage_buckets SET reserved_requests=GREATEST(0,reserved_requests-1),
              reserved_tokens=GREATEST(0,reserved_tokens-?), updated_at=NOW()
              WHERE scope=? AND scope_key=? AND window_start=?
            """, request.getReservedTokens(), bucket.scope, bucket.key, day);
    }
    private List<BucketRef> buckets(AiRequest request, LocalDate day) { return List.of(new BucketRef(USER, request.getUser().getId().toString()), new BucketRef(ACCOUNT, "global"), new BucketRef(MODEL, request.getModelId() + "|" + request.getRegion())); }
    private record BucketRef(String scope, String key) { }
    private AiRequest locked(UUID id) { return requests.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("AI request not found")); }
    private static boolean isTerminal(String status) { return List.of("COMPLETED", "FAILED", "CANCELLED", "EXPIRED").contains(status); }
    private static int nonnegative(int value) { if (value < 0) throw new IllegalArgumentException("Usage cannot be negative"); return value; }
    private static int intValue(Map<String,Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }
    private static void validateKey(String key) { if (key == null || !key.matches("[A-Za-z0-9._:-]{8,100}")) throw new IllegalArgumentException("Invalid idempotency key"); }
    private static void verifySameRequest(AiRequest request, ResolvedGeneration generation, int reserved) {
        if (!request.getPurpose().equals(generation.purpose().name()) || !request.getPolicyId().equals(generation.policyId())
                || !request.getModelId().equals(generation.modelId()) || request.getReservedTokens() != reserved)
            throw new UsageException(UsageException.Code.IDEMPOTENCY_CONFLICT);
    }
}
