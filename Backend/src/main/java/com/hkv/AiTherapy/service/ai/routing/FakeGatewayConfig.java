package com.hkv.AiTherapy.service.ai.routing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@Profile({"dev", "test"})
@ConditionalOnProperty(name = "ai.bedrock.enabled", havingValue = "false", matchIfMissing = true)
public class FakeGatewayConfig {
    @Bean
    public GenerationGateway generationGateway() {
        return new DeterministicFakeGateway();
    }
}
