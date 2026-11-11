package com.hkv.AiTherapy.service.chat;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitStatusExceptionTest {
    @Test
    void exposesRetryAfterWithoutMutatingReadOnlyHeaders() throws Exception {
        Class<?> type = Class.forName(ChatTurnWorkflow.class.getName() + "$RateLimitStatusException");
        var constructor = type.getDeclaredConstructor(long.class);
        constructor.setAccessible(true);
        var exception = (ResponseStatusException) constructor.newInstance(12L);
        assertEquals(429, exception.getStatusCode().value());
        assertEquals("12", exception.getHeaders().getFirst("Retry-After"));
    }
}
