package com.hkv.AiTherapy.service.ai;

import com.hkv.AiTherapy.dto.ai.AiMessage;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DisabledLegacyAIGatewayTest {
    private final DisabledLegacyAIGateway gateway = new DisabledLegacyAIGateway();
    private final List<AiMessage> messages = List.of(AiMessage.user("synthetic request"));

    @Test
    void legacySynchronousCallsFailClosedWithNoProviderFallback() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> gateway.generateChatResponse(messages, false));
        assertEquals(503, error.getStatusCode().value());
        assertTrue(error.getReason().contains("version 2"));
    }

    @Test
    void legacyStreamFailsOnceWithoutEmittingContentOrCompletingSuccessfully() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<String> output = new AtomicReference<>();
        AtomicReference<Boolean> completed = new AtomicReference<>(false);
        gateway.streamChatResponse(messages, false, output::set, () -> completed.set(true), failure::set);
        assertInstanceOf(ResponseStatusException.class, failure.get());
        assertNull(output.get());
        assertFalse(completed.get());
    }
}
