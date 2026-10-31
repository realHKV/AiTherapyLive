package com.hkv.AiTherapy.service.ai.bedrock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;

import java.time.Duration;

/** AWS client is created only when explicitly enabled; credentials come from the AWS SDK chain. */
@Configuration
@ConditionalOnProperty(name = "ai.bedrock.enabled", havingValue = "true")
public class BedrockClientConfig {
    private final String region;
    private final Duration connectTimeout;
    private final Duration apiCallTimeout;
    private final Duration apiCallAttemptTimeout;

    public BedrockClientConfig(@Value("${ai.bedrock.region:${BEDROCK_REGION:}}") String region,
                               @Value("${ai.bedrock.connect-timeout:2s}") Duration connectTimeout,
                               @Value("${ai.bedrock.api-call-timeout:45s}") Duration apiCallTimeout,
                               @Value("${ai.bedrock.api-call-attempt-timeout:40s}") Duration apiCallAttemptTimeout) {
        this.region = required(region, "ai.bedrock.region");
        this.connectTimeout = positive(connectTimeout, "ai.bedrock.connect-timeout");
        this.apiCallTimeout = positive(apiCallTimeout, "ai.bedrock.api-call-timeout");
        this.apiCallAttemptTimeout = positive(apiCallAttemptTimeout, "ai.bedrock.api-call-attempt-timeout");
        if (apiCallAttemptTimeout.compareTo(apiCallTimeout) > 0)
            throw new IllegalStateException("ai.bedrock.api-call-attempt-timeout must not exceed api-call-timeout");
    }

    @Bean(destroyMethod = "close")
    public BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient() {
        return BedrockRuntimeAsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                        .connectionTimeout(connectTimeout)
                        .maxConcurrency(64))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(apiCallTimeout)
                        .apiCallAttemptTimeout(apiCallAttemptTimeout)
                        // Application-level retries must be accounted as distinct provider attempts.
                        .retryPolicy(RetryPolicy.none())
                        .build())
                .build();
    }

    @Bean
    public BedrockGateway bedrockGateway(BedrockRuntimeAsyncClient client) {
        return new BedrockGateway(client, apiCallTimeout);
    }

    private static String required(String value, String key) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("unconfigured"))
            throw new IllegalStateException(key + " must be set when Bedrock is enabled");
        String trimmed = value.trim();
        if (!trimmed.matches("[a-z]{2}(?:-[a-z]+)+-[0-9]+"))
            throw new IllegalStateException(key + " is not a valid AWS region");
        try { Region.of(trimmed); }
        catch (RuntimeException e) { throw new IllegalStateException(key + " is not a valid AWS region", e); }
        return trimmed;
    }

    private static Duration positive(Duration value, String key) {
        if (value == null || value.isZero() || value.isNegative())
            throw new IllegalStateException(key + " must be greater than zero");
        return value;
    }
}
