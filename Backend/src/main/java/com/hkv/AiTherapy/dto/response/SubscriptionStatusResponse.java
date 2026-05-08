package com.hkv.AiTherapy.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Returned from GET /payment/status
 */
@Data
@Builder
public class SubscriptionStatusResponse {
    private String tier;           // "FREE" or "PRO"
    private Instant proExpiresAt;  // null for FREE

    /** Jackson strips the 'is' prefix from boolean getters — @JsonProperty forces the name. */
    @JsonProperty("isPro")
    private boolean isPro;
}
