package com.hkv.AiTherapy.service.ai.routing;

import java.util.List;
import java.util.UUID;

/** The caller supplies intent and content, never effective tier or model. */
public record GenerationCommand(UUID userId, GenerationPurpose purpose,
                                List<GenerationMessage> messages, int maxOutputTokens,
                                String requestedModelId) {
    public GenerationCommand {
        if (userId == null || purpose == null || messages == null || messages.isEmpty()
                || messages.stream().anyMatch(java.util.Objects::isNull)
                || maxOutputTokens < 1 || maxOutputTokens > 8192)
            throw new IllegalArgumentException("Invalid generation command");
        messages = List.copyOf(messages);
    }

    public static GenerationCommand serverSelected(UUID userId, GenerationPurpose purpose,
                                                    List<GenerationMessage> messages, int maxOutputTokens) {
        return new GenerationCommand(userId, purpose, messages, maxOutputTokens, null);
    }
}
