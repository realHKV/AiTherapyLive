package com.hkv.AiTherapy.dto;

import com.hkv.AiTherapy.dto.response.ApiResponse;
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
}
