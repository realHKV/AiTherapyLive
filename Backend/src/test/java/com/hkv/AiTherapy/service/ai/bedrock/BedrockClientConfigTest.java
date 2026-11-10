package com.hkv.AiTherapy.service.ai.bedrock;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class BedrockClientConfigTest {
    @Test
    void bedrockRequiresAValidRegion() {
        assertThrows(IllegalStateException.class, () -> new BedrockClientConfig("", Duration.ofSeconds(1),
                Duration.ofSeconds(4), Duration.ofSeconds(3)));
        assertThrows(IllegalStateException.class, () -> new BedrockClientConfig("unconfigured", Duration.ofSeconds(1),
                Duration.ofSeconds(4), Duration.ofSeconds(3)));
        assertThrows(IllegalStateException.class, () -> new BedrockClientConfig("not-a-region", Duration.ofSeconds(1),
                Duration.ofSeconds(4), Duration.ofSeconds(3)));
    }

    @Test
    void attemptDeadlineCannotExceedTheTotalDeadline() {
        assertThrows(IllegalStateException.class, () -> new BedrockClientConfig("ap-south-1", Duration.ofSeconds(1),
                Duration.ofSeconds(3), Duration.ofSeconds(4)));
        assertDoesNotThrow(() -> new BedrockClientConfig("ap-south-1", Duration.ofSeconds(1),
                Duration.ofSeconds(4), Duration.ofSeconds(3)));
    }
}
