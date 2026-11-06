package com.hkv.AiTherapy.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data @Builder
public class UsageStatusResponse {
    private String tier;
    private String policyId;
    private int remainingRequests;
    private int remainingTokens;
    private int reservedTokens;
    private Instant resetAt;
}
