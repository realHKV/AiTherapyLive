package com.hkv.AiTherapy.service.ai.routing;

public record GenerationMessage(Role role, String content) {
    public enum Role { SYSTEM, USER, ASSISTANT }

    public GenerationMessage {
        if (role == null || content == null || content.isBlank())
            throw new IllegalArgumentException("Generation message requires role and content");
    }
}
