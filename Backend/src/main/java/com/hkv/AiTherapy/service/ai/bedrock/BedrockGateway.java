package com.hkv.AiTherapy.service.ai.bedrock;

import com.hkv.AiTherapy.service.ai.routing.CancellationToken;
import com.hkv.AiTherapy.service.ai.routing.GenerationEvent;
import com.hkv.AiTherapy.service.ai.routing.GenerationException;
import com.hkv.AiTherapy.service.ai.routing.GenerationFailure;
import com.hkv.AiTherapy.service.ai.routing.GenerationGateway;
import com.hkv.AiTherapy.service.ai.routing.GenerationMessage;
import com.hkv.AiTherapy.service.ai.routing.GenerationResult;
import com.hkv.AiTherapy.service.ai.routing.GenerationUsage;
import com.hkv.AiTherapy.service.ai.routing.ResolvedGeneration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler.Visitor;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ModelNotReadyException;
import software.amazon.awssdk.services.bedrockruntime.model.ModelTimeoutException;
import software.amazon.awssdk.services.bedrockruntime.model.ResourceNotFoundException;
import software.amazon.awssdk.services.bedrockruntime.model.ServiceUnavailableException;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Bedrock Converse adapter. It emits only provider-neutral data and sanitized failure categories. */
public final class BedrockGateway implements GenerationGateway {
    private final BedrockRuntimeAsyncClient client;
    private final Duration callTimeout;

    public BedrockGateway(BedrockRuntimeAsyncClient client, Duration callTimeout) {
        if (client == null || callTimeout == null || callTimeout.isZero() || callTimeout.isNegative())
            throw new IllegalArgumentException("Bedrock client and positive timeout are required");
        this.client = client;
        this.callTimeout = callTimeout;
    }

    @Override
    public GenerationResult generate(ResolvedGeneration request, CancellationToken cancellation) {
        requireRequest(request, cancellation);
        CompletableFuture<ConverseResponse> future;
        try { future = client.converse(converseRequest(request)); }
        catch (RuntimeException e) { throw new GenerationException(mapFailure(e)); }
        try (AutoCloseable ignored = cancellation.onCancel(() -> future.cancel(true))) {
            return toResult(await(future, cancellation), request);
        } catch (GenerationException e) {
            throw e;
        } catch (Exception e) {
            throw failure(GenerationFailure.Code.UNAVAILABLE, false);
        }
    }

    @Override
    public void stream(ResolvedGeneration request, CancellationToken cancellation, Consumer<GenerationEvent> events) {
        requireRequest(request, cancellation);
        if (events == null) throw new IllegalArgumentException("Event consumer is required");

        StringBuilder text = new StringBuilder();
        AtomicBoolean started = new AtomicBoolean();
        AtomicBoolean terminal = new AtomicBoolean();
        AtomicReference<StopReason> stopReason = new AtomicReference<>();
        AtomicReference<GenerationUsage> usage = new AtomicReference<>(GenerationUsage.unknown());
        Runnable start = () -> {
            if (started.compareAndSet(false, true)) events.accept(new GenerationEvent.Started(request.policyId(), request.modelId()));
        };
        Visitor visitor = Visitor.builder()
                .onContentBlockDelta(event -> emitText(event, cancellation, events, text, start))
                .onMessageStop(event -> stopReason.set(event.stopReason()))
                .onMetadata(event -> usage.set(toUsage(event)))
                .build();
        CompletableFuture<Void> future;
        try {
            future = client.converseStream(streamRequest(request),
                    ConverseStreamResponseHandler.builder().subscriber(visitor).build());
        } catch (RuntimeException e) {
            events.accept(new GenerationEvent.Failed(mapFailure(e)));
            return;
        }

        try (AutoCloseable ignored = cancellation.onCancel(() -> future.cancel(true))) {
            await(future, cancellation);
            if (cancellation.isCancelled()) throw failure(GenerationFailure.Code.CANCELLED, false);
            if (text.isEmpty()) throw failure(GenerationFailure.Code.INVALID_RESPONSE, false);
            start.run();
            GenerationResult result = new GenerationResult(text.toString(), finish(stopReason.get()), usage.get(),
                    request.policyId(), request.modelId());
            if (terminal.compareAndSet(false, true)) events.accept(new GenerationEvent.Completed(result));
        } catch (GenerationException e) {
            if (terminal.compareAndSet(false, true)) events.accept(new GenerationEvent.Failed(e.failure()));
        } catch (Exception e) {
            GenerationFailure mapped = mapFailure(unwrap(e));
            if (terminal.compareAndSet(false, true)) events.accept(new GenerationEvent.Failed(mapped));
        }
    }

    private <T> T await(CompletableFuture<T> future, CancellationToken cancellation) {
        try {
            return future.get(callTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw failure(GenerationFailure.Code.TIMEOUT, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw failure(cancellation.isCancelled() ? GenerationFailure.Code.CANCELLED : GenerationFailure.Code.TIMEOUT, true);
        } catch (CancellationException e) {
            throw failure(GenerationFailure.Code.CANCELLED, false);
        } catch (ExecutionException e) {
            throw new GenerationException(mapFailure(unwrap(e.getCause())));
        }
    }

    private ConverseRequest converseRequest(ResolvedGeneration request) {
        List<SystemContentBlock> system = new ArrayList<>();
        List<Message> messages = new ArrayList<>();
        for (GenerationMessage source : request.messages()) {
            if (source.role() == GenerationMessage.Role.SYSTEM) {
                system.add(SystemContentBlock.builder().text(source.content()).build());
            } else {
                messages.add(Message.builder()
                        .role(source.role() == GenerationMessage.Role.USER
                                ? software.amazon.awssdk.services.bedrockruntime.model.ConversationRole.USER
                                : software.amazon.awssdk.services.bedrockruntime.model.ConversationRole.ASSISTANT)
                        .content(ContentBlock.builder().text(source.content()).build())
                        .build());
            }
        }
        if (messages.isEmpty()) throw failure(GenerationFailure.Code.INVALID_REQUEST, false);
        return ConverseRequest.builder().modelId(request.modelId()).system(system).messages(messages)
                .inferenceConfig(config -> config.maxTokens(request.maxOutputTokens())).build();
    }

    private ConverseStreamRequest streamRequest(ResolvedGeneration request) {
        ConverseRequest common = converseRequest(request);
        return ConverseStreamRequest.builder().modelId(common.modelId()).system(common.system())
                .messages(common.messages()).inferenceConfig(common.inferenceConfig()).build();
    }

    private GenerationResult toResult(ConverseResponse response, ResolvedGeneration request) {
        if (response == null || response.output() == null || response.output().message() == null)
            throw failure(GenerationFailure.Code.INVALID_RESPONSE, false);
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : response.output().message().content()) {
            if (block.text() != null) text.append(block.text());
        }
        if (text.isEmpty()) throw failure(GenerationFailure.Code.INVALID_RESPONSE, false);
        return new GenerationResult(text.toString(), finish(response.stopReason()),
                response.usage() == null ? GenerationUsage.unknown() : toUsage(response.usage()),
                request.policyId(), request.modelId());
    }

    private void emitText(ContentBlockDeltaEvent event, CancellationToken cancellation, Consumer<GenerationEvent> events,
                          StringBuilder text, Runnable start) {
        if (cancellation.isCancelled()) return;
        String delta = event.delta() == null ? null : event.delta().text();
        if (delta == null || delta.isEmpty()) return;
        start.run();
        text.append(delta);
        events.accept(new GenerationEvent.Delta(delta));
    }

    private static GenerationUsage toUsage(ConverseStreamMetadataEvent event) {
        return event == null || event.usage() == null ? GenerationUsage.unknown() : toUsage(event.usage());
    }
    private static GenerationUsage toUsage(TokenUsage usage) {
        return new GenerationUsage(usage.inputTokens(), usage.outputTokens());
    }
    private static GenerationResult.Finish finish(StopReason reason) {
        return reason == StopReason.MAX_TOKENS ? GenerationResult.Finish.LENGTH_LIMIT : GenerationResult.Finish.COMPLETED;
    }

    private static GenerationFailure mapFailure(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof ThrottlingException || cause instanceof ModelNotReadyException)
            return new GenerationFailure(GenerationFailure.Code.THROTTLED, true);
        if (cause instanceof ModelTimeoutException || cause instanceof TimeoutException
                || cause instanceof ApiCallTimeoutException || cause instanceof ApiCallAttemptTimeoutException)
            return new GenerationFailure(GenerationFailure.Code.TIMEOUT, true);
        if (cause instanceof AccessDeniedException)
            return new GenerationFailure(GenerationFailure.Code.ACCESS_DENIED, false);
        if (cause instanceof ResourceNotFoundException)
            return new GenerationFailure(GenerationFailure.Code.MODEL_NOT_FOUND, false);
        if (cause instanceof ValidationException)
            return new GenerationFailure(GenerationFailure.Code.INVALID_REQUEST, false);
        if (cause instanceof ServiceUnavailableException)
            return new GenerationFailure(GenerationFailure.Code.UNAVAILABLE, true);
        if (cause instanceof CancellationException || cause instanceof InterruptedException)
            return new GenerationFailure(GenerationFailure.Code.CANCELLED, false);
        if (cause instanceof SdkClientException)
            return new GenerationFailure(GenerationFailure.Code.UNAVAILABLE, true);
        if (cause instanceof SdkException)
            return new GenerationFailure(GenerationFailure.Code.UNAVAILABLE, false);
        return new GenerationFailure(GenerationFailure.Code.UNAVAILABLE, false);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current != null && (current instanceof ExecutionException
                || current instanceof java.util.concurrent.CompletionException)) current = current.getCause();
        return current == null ? error : current;
    }
    private static GenerationException failure(GenerationFailure.Code code, boolean retryable) {
        return new GenerationException(new GenerationFailure(code, retryable));
    }
    private static void requireRequest(ResolvedGeneration request, CancellationToken cancellation) {
        if (request == null || cancellation == null) throw new IllegalArgumentException("Request and cancellation token are required");
        if (cancellation.isCancelled()) throw failure(GenerationFailure.Code.CANCELLED, false);
    }
}
