package com.hkv.AiTherapy.service.ai.routing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Model identifiers are deployment inputs; no candidate model is assumed to work. */
@Component
@ConditionalOnProperty(name = "ai.routing.enabled", havingValue = "true")
public class GenerationPolicy {
    private final String policyId;
    private final String freeModelId;
    private final String proModelId;
    private final String summaryModelId;

    public GenerationPolicy(@Value("${ai.routing.policy-id:}") String policyId,
                            @Value("${ai.routing.free-model-id:}") String freeModelId,
                            @Value("${ai.routing.pro-model-id:}") String proModelId,
                            @Value("${ai.routing.summary-model-id:}") String summaryModelId) {
        this.policyId = required(policyId, "ai.routing.policy-id");
        this.freeModelId = required(freeModelId, "ai.routing.free-model-id");
        this.proModelId = required(proModelId, "ai.routing.pro-model-id");
        this.summaryModelId = required(summaryModelId, "ai.routing.summary-model-id");
    }

    private static String required(String value, String key) {
        if (value == null || value.isBlank()) throw new IllegalStateException(key + " must be configured");
        return value.trim();
    }

    public String policyId() { return policyId; }

    public String modelFor(GenerationPurpose purpose, ResolvedGeneration.Tier tier) {
        if (purpose == null || tier == null) throw new IllegalArgumentException("Purpose and tier required");
        return switch (purpose) {
            case GREETING, CHAT -> tier == ResolvedGeneration.Tier.PRO ? proModelId : freeModelId;
            case SUMMARY, PROFILE_REFRESH -> summaryModelId;
        };
    }
}
