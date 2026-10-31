package com.hkv.AiTherapy.service.ai.routing;

import java.util.function.Consumer;

/** Fixed synthetic output for tests and local development; never invokes a provider. */
public final class DeterministicFakeGateway implements GenerationGateway {
    @Override
    public GenerationResult generate(ResolvedGeneration request, CancellationToken cancellation) {
        if (cancellation.isCancelled())
            throw new GenerationException(new GenerationFailure(GenerationFailure.Code.CANCELLED, false));
        String text = request.purpose() == GenerationPurpose.SUMMARY
                ? "{\"summary\":\"Synthetic session summary.\",\"memories\":[]}"
                : "Synthetic response for " + request.purpose().name() + ".";
        return new GenerationResult(text, GenerationResult.Finish.COMPLETED,
                GenerationUsage.unknown(), request.policyId(), request.modelId());
    }

    @Override
    public void stream(ResolvedGeneration request, CancellationToken cancellation, Consumer<GenerationEvent> events) {
        if (cancellation.isCancelled()) {
            events.accept(new GenerationEvent.Failed(new GenerationFailure(GenerationFailure.Code.CANCELLED, false)));
            return;
        }
        events.accept(new GenerationEvent.Started(request.policyId(), request.modelId()));
        GenerationResult result;
        try {
            result = generate(request, cancellation);
        } catch (GenerationException e) {
            events.accept(new GenerationEvent.Failed(e.failure()));
            return;
        }
        events.accept(new GenerationEvent.Delta(result.text()));
        if (cancellation.isCancelled()) {
            events.accept(new GenerationEvent.Failed(new GenerationFailure(GenerationFailure.Code.CANCELLED, false)));
        } else {
            events.accept(new GenerationEvent.Completed(result));
        }
    }
}
