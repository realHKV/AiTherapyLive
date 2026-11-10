package com.hkv.AiTherapy.service.ai.routing;

public record GenerationResult(String text, Finish finish, GenerationUsage usage,
                               String policyId, String modelId) {
    public enum Finish { COMPLETED, STOPPED, LENGTH_LIMIT, CANCELLED }

    public GenerationResult {
        if (text == null || finish == null || usage == null || policyId == null || modelId == null)
            throw new IllegalArgumentException("Invalid generation result");
    }
}
