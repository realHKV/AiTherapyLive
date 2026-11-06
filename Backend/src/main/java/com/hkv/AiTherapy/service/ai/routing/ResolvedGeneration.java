package com.hkv.AiTherapy.service.ai.routing;

import java.util.List;
import java.util.UUID;

/** Immutable, server-approved routing decision passed to a provider adapter. */
public record ResolvedGeneration(UUID userId, GenerationPurpose purpose, Tier tier,
                                 String policyId, String modelId,
                                 List<GenerationMessage> messages, int maxOutputTokens) {
    public enum Tier { FREE, PRO }

    public ResolvedGeneration {
        if (userId == null || purpose == null || tier == null || policyId == null || policyId.isBlank()
                || modelId == null || modelId.isBlank() || messages == null || messages.isEmpty()
                || maxOutputTokens < 1)
            throw new IllegalArgumentException("Invalid resolved generation");
        messages = List.copyOf(messages);
    }
}
