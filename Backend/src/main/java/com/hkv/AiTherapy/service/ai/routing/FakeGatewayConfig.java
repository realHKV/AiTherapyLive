package com.hkv.AiTherapy.service.ai.routing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile({"dev", "test"})
public class FakeGatewayConfig {
    @Bean
    public GenerationGateway generationGateway() {
        return new DeterministicFakeGateway();
    }
}
