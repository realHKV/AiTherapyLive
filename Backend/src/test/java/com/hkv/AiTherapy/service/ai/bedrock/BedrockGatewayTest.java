package com.hkv.AiTherapy.service.ai.bedrock;

import com.hkv.AiTherapy.service.ai.routing.CancellationToken;
import com.hkv.AiTherapy.service.ai.routing.GenerationEvent;
import com.hkv.AiTherapy.service.ai.routing.GenerationException;
import com.hkv.AiTherapy.service.ai.routing.GenerationFailure;
import com.hkv.AiTherapy.service.ai.routing.GenerationMessage;
import com.hkv.AiTherapy.service.ai.routing.GenerationPurpose;
import com.hkv.AiTherapy.service.ai.routing.ResolvedGeneration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ResourceNotFoundException;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BedrockGatewayTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000021");

    private ResolvedGeneration request() {
        return new ResolvedGeneration(USER_ID, GenerationPurpose.CHAT, ResolvedGeneration.Tier.FREE,
                "test-policy", "test-model-id", List.of(
                new GenerationMessage(GenerationMessage.Role.SYSTEM, "Synthetic system instruction"),
                new GenerationMessage(GenerationMessage.Role.USER, "Synthetic user message")), 64);
    }

    @Test
    void generateMapsConversationTextUsageAndServerSelectedIdentity() {
        BedrockRuntimeAsyncClient client = mock(BedrockRuntimeAsyncClient.class);
        ConverseResponse response = ConverseResponse.builder()
                .output(ConverseOutput.builder().message(Message.builder().role(ConversationRole.ASSISTANT)
                        .content(ContentBlock.builder().text("Synthetic answer.").build()).build()).build())
                .stopReason(StopReason.END_TURN)
                .usage(TokenUsage.builder().inputTokens(12).outputTokens(4).build())
                .build();
        when(client.converse(any(ConverseRequest.class))).thenReturn(CompletableFuture.completedFuture(response));

        var result = new BedrockGateway(client, Duration.ofSeconds(2)).generate(request(), new CancellationToken());

        assertEquals("Synthetic answer.", result.text());
        assertEquals("test-policy", result.policyId());
        assertEquals("test-model-id", result.modelId());
        assertEquals(12, result.usage().inputTokens());
        assertEquals(4, result.usage().outputTokens());
        var captor = org.mockito.ArgumentCaptor.forClass(ConverseRequest.class);
        verify(client).converse(captor.capture());
        assertEquals("test-model-id", captor.getValue().modelId());
        assertEquals(1, captor.getValue().system().size());
        assertEquals(1, captor.getValue().messages().size());
        assertEquals(64, captor.getValue().inferenceConfig().maxTokens());
    }

    @Test
    void providerErrorsBecomeSanitizedFailureCategories() {
        BedrockRuntimeAsyncClient client = mock(BedrockRuntimeAsyncClient.class);
        when(client.converse(any(ConverseRequest.class))).thenReturn(CompletableFuture.failedFuture(
                ThrottlingException.builder().message("synthetic account detail").build()));

        GenerationException failure = assertThrows(GenerationException.class,
                () -> new BedrockGateway(client, Duration.ofSeconds(2)).generate(request(), new CancellationToken()));
        assertEquals(GenerationFailure.Code.THROTTLED, failure.failure().code());
        assertTrue(failure.failure().retryable());
        assertFalse(failure.getMessage().contains("synthetic account detail"));

        when(client.converse(any(ConverseRequest.class))).thenReturn(CompletableFuture.failedFuture(
                AccessDeniedException.builder().message("synthetic IAM detail").build()));
        failure = assertThrows(GenerationException.class,
                () -> new BedrockGateway(client, Duration.ofSeconds(2)).generate(request(), new CancellationToken()));
        assertEquals(GenerationFailure.Code.ACCESS_DENIED, failure.failure().code());
        assertFalse(failure.failure().retryable());
    }

    @Test
    void missingModelIsMappedWithoutLeakingAwsDetails() {
        BedrockRuntimeAsyncClient client = mock(BedrockRuntimeAsyncClient.class);
        when(client.converse(any(ConverseRequest.class))).thenReturn(CompletableFuture.failedFuture(
                ResourceNotFoundException.builder().message("synthetic model access detail").build()));
        GenerationException failure = assertThrows(GenerationException.class,
                () -> new BedrockGateway(client, Duration.ofSeconds(2)).generate(request(), new CancellationToken()));
        assertEquals(GenerationFailure.Code.MODEL_NOT_FOUND, failure.failure().code());
        assertFalse(failure.getMessage().contains("synthetic model access detail"));
    }

    @Test
    void timeoutCancelsRequestAndExplicitCancellationCancelsInflightRequest() {
        BedrockRuntimeAsyncClient client = mock(BedrockRuntimeAsyncClient.class);
        CompletableFuture<ConverseResponse> neverCompletes = new CompletableFuture<>();
        when(client.converse(any(ConverseRequest.class))).thenReturn(neverCompletes);
        GenerationException failure = assertThrows(GenerationException.class,
                () -> new BedrockGateway(client, Duration.ofMillis(5)).generate(request(), new CancellationToken()));
        assertEquals(GenerationFailure.Code.TIMEOUT, failure.failure().code());
        assertTrue(neverCompletes.isCancelled());

        CompletableFuture<ConverseResponse> inFlight = new CompletableFuture<>();
        when(client.converse(any(ConverseRequest.class))).thenReturn(inFlight);
        CancellationToken cancellation = new CancellationToken();
        Thread worker = new Thread(() -> assertThrows(GenerationException.class,
                () -> new BedrockGateway(client, Duration.ofSeconds(2)).generate(request(), cancellation)));
        worker.start();
        verify(client, timeout(500).atLeastOnce()).converse(any(ConverseRequest.class));
        cancellation.cancel();
        try { worker.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); fail(e); }
        assertFalse(worker.isAlive());
        assertTrue(inFlight.isCancelled());
    }

    @Test
    void streamEmitsDeltasUsageAndOneTerminalCompletion() {
        BedrockRuntimeAsyncClient client = mock(BedrockRuntimeAsyncClient.class);
        when(client.converseStream(any(ConverseStreamRequest.class), any(ConverseStreamResponseHandler.class)))
                .thenAnswer(invocation -> {
                    ConverseStreamResponseHandler handler = invocation.getArgument(1);
                    handler.onEventStream(SdkPublisher.fromIterable(List.of(
                            ConverseStreamOutput.contentBlockDeltaBuilder()
                                    .delta(ContentBlockDelta.fromText("Synthetic ")).build(),
                            ConverseStreamOutput.contentBlockDeltaBuilder()
                                    .delta(ContentBlockDelta.fromText("stream")).build(),
                            ConverseStreamOutput.messageStopBuilder().stopReason(StopReason.END_TURN).build(),
                            ConverseStreamOutput.metadataBuilder().usage(
                                    TokenUsage.builder().inputTokens(5).outputTokens(2).build()).build())));
                    handler.complete();
                    return CompletableFuture.completedFuture(null);
                });

        List<GenerationEvent> events = new ArrayList<>();
        new BedrockGateway(client, Duration.ofSeconds(2)).stream(request(), new CancellationToken(), events::add);

        assertInstanceOf(GenerationEvent.Started.class, events.get(0));
        assertEquals("Synthetic ", ((GenerationEvent.Delta) events.get(1)).text());
        assertEquals("stream", ((GenerationEvent.Delta) events.get(2)).text());
        assertInstanceOf(GenerationEvent.Completed.class, events.get(3));
        var completed = (GenerationEvent.Completed) events.get(3);
        assertEquals("Synthetic stream", completed.result().text());
        assertEquals(5, completed.result().usage().inputTokens());
        assertEquals(2, completed.result().usage().outputTokens());
        assertEquals(1, events.stream().filter(e -> e instanceof GenerationEvent.Completed || e instanceof GenerationEvent.Failed).count());
    }

    @Test
    void streamFailureAfterPartialOutputIsTerminalFailure() {
        BedrockRuntimeAsyncClient client = mock(BedrockRuntimeAsyncClient.class);
        when(client.converseStream(any(ConverseStreamRequest.class), any(ConverseStreamResponseHandler.class)))
                .thenAnswer(invocation -> {
                    ConverseStreamResponseHandler handler = invocation.getArgument(1);
                    handler.onEventStream(SdkPublisher.fromIterable(List.of(ConverseStreamOutput.contentBlockDeltaBuilder()
                            .delta(ContentBlockDelta.fromText("Partial synthetic text")).build())));
                    handler.exceptionOccurred(ThrottlingException.builder().message("synthetic throttle detail").build());
                    return CompletableFuture.failedFuture(ThrottlingException.builder().message("synthetic throttle detail").build());
                });

        List<GenerationEvent> events = new ArrayList<>();
        new BedrockGateway(client, Duration.ofSeconds(2)).stream(request(), new CancellationToken(), events::add);

        assertTrue(events.stream().anyMatch(e -> e instanceof GenerationEvent.Delta));
        assertEquals(GenerationFailure.Code.THROTTLED, ((GenerationEvent.Failed) events.get(events.size() - 1)).failure().code());
        assertEquals(1, events.stream().filter(e -> e instanceof GenerationEvent.Completed || e instanceof GenerationEvent.Failed).count());
    }
}
