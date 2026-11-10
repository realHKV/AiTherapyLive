package com.hkv.AiTherapy.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionRuntimeValidatorTest {
    private ApplicationContextRunner productionContext() {
        return new ApplicationContextRunner()
                .withUserConfiguration(ProductionRuntimeValidator.class)
                .withPropertyValues(
                        "spring.profiles.active=production",
                        "spring.datasource.url=jdbc:postgresql://db.example/therapy?sslmode=verify-full",
                        "spring.datasource.username=therapy_app",
                        "spring.datasource.password=synthetic-db-secret",
                        "spring.data.redis.host=redis.example",
                        "spring.data.redis.password=synthetic-redis-secret",
                        "spring.data.redis.ssl.enabled=true",
                        "jwt.private-key=synthetic-private-key",
                        "jwt.public-key=synthetic-public-key",
                        "encryption.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                        "spring.security.oauth2.client.registration.google.client-id=synthetic-client",
                        "spring.security.oauth2.client.registration.google.client-secret=synthetic-google-secret",
                        "razorpay.key-id=synthetic-key",
                        "razorpay.key-secret=synthetic-razorpay-secret",
                        "razorpay.webhook-secret=synthetic-webhook-secret",
                        "cors.allowed-origins=https://app.example.com",
                        "ai.bedrock.enabled=true",
                        "ai.bedrock.region=ap-south-1",
                        "ai.usage.region=ap-south-1",
                        "ai.routing.enabled=true",
                        "ai.chat.v2.enabled=true",
                        "ai.extraction.worker-enabled=true",
                        "app.dev-mode=false");
    }

    @Test
    void acceptsCompleteBedrockProductionConfiguration() {
        productionContext().run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void rejectsMisalignedBedrockAndUsageRegionsBeforeServingRequests() {
        productionContext().withPropertyValues("ai.usage.region=us-east-1").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("Bedrock and usage accounting regions must match and be configured");
        });
    }

    @Test
    void rejectsWildcardOrNonHttpsProductionOrigins() {
        productionContext().withPropertyValues("cors.allowed-origins=*").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("cors.allowed-origins must contain HTTPS origins without paths");
        });
    }

    @Test
    void rejectsProductionDatabaseOrRedisWithoutVerifiedTls() {
        productionContext().withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost/therapy")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("sslmode=verify-full");
                });
    }
}
