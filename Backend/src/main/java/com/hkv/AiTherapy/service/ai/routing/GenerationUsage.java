package com.hkv.AiTherapy.service.ai.routing;

/** Provider reported tokens; unknown usage stays unknown rather than becoming a billable zero. */
public record GenerationUsage(Integer inputTokens, Integer outputTokens) {
    public GenerationUsage {
        if ((inputTokens != null && inputTokens < 0) || (outputTokens != null && outputTokens < 0))
            throw new IllegalArgumentException("Token usage cannot be negative");
    }

    public static GenerationUsage unknown() { return new GenerationUsage(null, null); }
}
