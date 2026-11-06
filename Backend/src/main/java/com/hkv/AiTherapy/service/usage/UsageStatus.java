package com.hkv.AiTherapy.service.usage;

import com.hkv.AiTherapy.service.ai.routing.ResolvedGeneration;
import java.time.Instant;

public record UsageStatus(ResolvedGeneration.Tier tier, String policyId, int remainingRequests,
                          int remainingTokens, int reservedTokens, Instant resetAt) { }
