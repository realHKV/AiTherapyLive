package com.hkv.AiTherapy.service.ai.routing;

import java.util.function.Consumer;

/** Provider neutral boundary. Implementations must honor cancellation. */
public interface GenerationGateway {
    GenerationResult generate(ResolvedGeneration request, CancellationToken cancellation);
    void stream(ResolvedGeneration request, CancellationToken cancellation, Consumer<GenerationEvent> events);
}
