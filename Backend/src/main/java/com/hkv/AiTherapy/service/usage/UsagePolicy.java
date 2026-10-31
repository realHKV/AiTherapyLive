package com.hkv.AiTherapy.service.usage;

import com.hkv.AiTherapy.service.ai.routing.ResolvedGeneration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Limits are deployment configuration, never client input or hard-coded controller policy. */
@Component
public class UsagePolicy {
    private final String policyId;
    private final String region;
    private final int freeDailyRequests, proDailyRequests, freeDailyTokens, proDailyTokens;
    private final int accountDailyRequests, accountDailyTokens, modelDailyRequests, modelDailyTokens;
    private final int freeMinuteRequests, proMinuteRequests;
    private final Duration leaseDuration;

    public UsagePolicy(@Value("${ai.usage.policy-id:beta-v1}") String policyId,
            @Value("${ai.usage.region:unconfigured}") String region,
            @Value("${ai.usage.free.daily-requests:20}") int freeDailyRequests,
            @Value("${ai.usage.pro.daily-requests:60}") int proDailyRequests,
            @Value("${ai.usage.free.daily-tokens:24000}") int freeDailyTokens,
            @Value("${ai.usage.pro.daily-tokens:100000}") int proDailyTokens,
            @Value("${ai.usage.account.daily-requests:1000}") int accountDailyRequests,
            @Value("${ai.usage.account.daily-tokens:1000000}") int accountDailyTokens,
            @Value("${ai.usage.model.daily-requests:500}") int modelDailyRequests,
            @Value("${ai.usage.model.daily-tokens:500000}") int modelDailyTokens,
            @Value("${ai.usage.free.per-minute-requests:5}") int freeMinuteRequests,
            @Value("${ai.usage.pro.per-minute-requests:10}") int proMinuteRequests,
            @Value("${ai.usage.lease-seconds:120}") long leaseSeconds) {
        this.policyId = required(policyId, "ai.usage.policy-id");
        this.region = required(region, "ai.usage.region");
        this.freeDailyRequests = positive(freeDailyRequests); this.proDailyRequests = positive(proDailyRequests);
        this.freeDailyTokens = positive(freeDailyTokens); this.proDailyTokens = positive(proDailyTokens);
        this.accountDailyRequests = positive(accountDailyRequests); this.accountDailyTokens = positive(accountDailyTokens);
        this.modelDailyRequests = positive(modelDailyRequests); this.modelDailyTokens = positive(modelDailyTokens);
        this.freeMinuteRequests = positive(freeMinuteRequests); this.proMinuteRequests = positive(proMinuteRequests);
        this.leaseDuration = Duration.ofSeconds(positive(leaseSeconds));
    }
    private static String required(String value, String key) { if (value == null || value.isBlank()) throw new IllegalStateException(key + " must be configured"); return value.trim(); }
    private static int positive(int value) { if (value < 1) throw new IllegalStateException("Usage limits must be positive"); return value; }
    private static long positive(long value) { if (value < 1) throw new IllegalStateException("Usage limits must be positive"); return value; }
    public String policyId() { return policyId; }
    public String region() { return region; }
    public int userRequests(ResolvedGeneration.Tier tier) { return tier == ResolvedGeneration.Tier.PRO ? proDailyRequests : freeDailyRequests; }
    public int userTokens(ResolvedGeneration.Tier tier) { return tier == ResolvedGeneration.Tier.PRO ? proDailyTokens : freeDailyTokens; }
    public int minuteRequests(ResolvedGeneration.Tier tier) { return tier == ResolvedGeneration.Tier.PRO ? proMinuteRequests : freeMinuteRequests; }
    public int accountRequests() { return accountDailyRequests; } public int accountTokens() { return accountDailyTokens; }
    public int modelRequests() { return modelDailyRequests; } public int modelTokens() { return modelDailyTokens; }
    public Duration leaseDuration() { return leaseDuration; }
}
