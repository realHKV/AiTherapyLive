package com.hkv.AiTherapy.dto;

import com.hkv.AiTherapy.dto.response.ApiResponse;
import com.hkv.AiTherapy.dto.response.MemoryResponse;
import com.hkv.AiTherapy.dto.response.SubscriptionStatusResponse;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@JsonTest
class ApiResponseJsonTest {

    @Autowired
    private ObjectMapper mapper;

    @Test
    void successEnvelopeKeepsBooleanAndIsoTimestamp() throws Exception {
        ApiResponse<Map<String, String>> response = new ApiResponse<>(
                true, Map.of("value", "synthetic"), null, Instant.parse("2026-01-01T00:00:00Z"));

        var json = mapper.readTree(mapper.writeValueAsString(response));
        assertTrue(json.get("success").asBoolean());
        assertEquals("synthetic", json.path("data").path("value").asText());
        assertEquals("2026-01-01T00:00:00Z", json.path("timestamp").asText());
    }

    @Test
    void errorEnvelopeKeepsCodeAndMessage() throws Exception {
        var json = mapper.readTree(mapper.writeValueAsString(
                ApiResponse.error("SYNTHETIC_ERROR", "Fixture only")));
        assertFalse(json.get("success").asBoolean());
        assertEquals("SYNTHETIC_ERROR", json.path("error").path("code").asText());
        assertEquals("Fixture only", json.path("error").path("message").asText());
    }

    @Test
    void publicBooleanNamesAndNullableDatesAreStable() throws Exception {
        var memory = mapper.readTree(mapper.writeValueAsString(MemoryResponse.builder()
                .id("00000000-0000-0000-0000-000000000001")
                .type("goal").title("Synthetic goal").importance(5).isResolved(false).build()));
        assertFalse(memory.path("resolved").asBoolean());
        assertTrue(memory.has("resolved"));
        assertFalse(memory.has("isResolved"));
        assertTrue(memory.path("occurredAt").isNull());

        var subscription = mapper.readTree(mapper.writeValueAsString(SubscriptionStatusResponse.builder()
                .tier("FREE").isPro(false).build()));
        assertFalse(subscription.path("isPro").asBoolean());
        assertTrue(subscription.has("isPro"));
        assertTrue(subscription.path("proExpiresAt").isNull());
    }
}
