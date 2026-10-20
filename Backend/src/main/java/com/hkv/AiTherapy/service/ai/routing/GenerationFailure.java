package com.hkv.AiTherapy.service.ai.routing;

/** Sanitized failure category; adapters must not expose provider payloads or secrets. */
public record GenerationFailure(Code code, boolean retryable) {
    public enum Code { UNAVAILABLE, THROTTLED, TIMEOUT, CANCELLED, INVALID_RESPONSE }

    public GenerationFailure {
        if (code == null) throw new IllegalArgumentException("Failure code required");
    }
}
