package com.hkv.AiTherapy.service.ai.routing;

/** A stream has exactly one terminal completed or error event. */
public sealed interface GenerationEvent permits GenerationEvent.Started, GenerationEvent.Delta,
        GenerationEvent.Completed, GenerationEvent.Failed {
    record Started(String policyId, String modelId) implements GenerationEvent { }
    record Delta(String text) implements GenerationEvent { }
    record Completed(GenerationResult result) implements GenerationEvent { }
    record Failed(GenerationFailure failure) implements GenerationEvent { }
}
