package com.hkv.AiTherapy.service.ai.routing;

public final class GenerationException extends RuntimeException {
    private final GenerationFailure failure;

    public GenerationException(GenerationFailure failure) {
        super(failure.code().name());
        this.failure = failure;
    }

    public GenerationFailure failure() { return failure; }
}
