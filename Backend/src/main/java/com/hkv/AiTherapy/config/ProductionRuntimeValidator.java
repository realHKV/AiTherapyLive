package com.hkv.AiTherapy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

/** Rejects unsafe or incomplete production configuration before the HTTP server accepts traffic. */
@Component
@Profile("production")
public class ProductionRuntimeValidator {
    public ProductionRuntimeValidator(
            @Value("${spring.datasource.url:}") String databaseUrl,
            @Value("${spring.datasource.username:}") String databaseUser,
            @Value("${spring.datasource.password:}") String databasePassword,
            @Value("${spring.data.redis.host:}") String redisHost,
            @Value("${spring.data.redis.password:}") String redisPassword,
            @Value("${spring.data.redis.ssl.enabled:false}") boolean redisTlsEnabled,
            @Value("${jwt.private-key:}") String jwtPrivateKey,
            @Value("${jwt.public-key:}") String jwtPublicKey,
            @Value("${encryption.master-key:}") String encryptionKey,
            @Value("${spring.security.oauth2.client.registration.google.client-id:}") String googleClientId,
            @Value("${spring.security.oauth2.client.registration.google.client-secret:}") String googleClientSecret,
            @Value("${razorpay.key-id:}") String razorpayKeyId,
            @Value("${razorpay.key-secret:}") String razorpayKeySecret,
            @Value("${razorpay.webhook-secret:}") String razorpayWebhookSecret,
            @Value("${cors.allowed-origins:}") String corsOrigins,
            @Value("${ai.bedrock.enabled:false}") boolean bedrockEnabled,
            @Value("${ai.bedrock.region:}") String bedrockRegion,
            @Value("${ai.usage.region:}") String usageRegion,
            @Value("${ai.routing.enabled:false}") boolean routingEnabled,
            @Value("${ai.chat.v2.enabled:false}") boolean v2Enabled,
            @Value("${ai.extraction.worker-enabled:false}") boolean extractionWorkerEnabled,
            @Value("${app.dev-mode:false}") boolean devMode) {
        require("spring.datasource.url", databaseUrl);
        require("spring.datasource.username", databaseUser);
        require("spring.datasource.password", databasePassword);
        require("spring.data.redis.host", redisHost);
        require("spring.data.redis.password", redisPassword);
        if (databaseUrl.contains("localhost") || databaseUrl.contains("127.0.0.1")
                || !databaseUrl.contains("sslmode=verify-full"))
            throw new IllegalStateException("Production DATABASE_URL must use a remote PostgreSQL host and sslmode=verify-full");
        if (redisHost.equalsIgnoreCase("localhost") || redisHost.equals("127.0.0.1") || !redisTlsEnabled)
            throw new IllegalStateException("Production Redis must use a remote host with TLS enabled");
        require("jwt.private-key", jwtPrivateKey);
        require("jwt.public-key", jwtPublicKey);
        require("encryption.master-key", encryptionKey);
        require("spring.security.oauth2.client.registration.google.client-id", googleClientId);
        require("spring.security.oauth2.client.registration.google.client-secret", googleClientSecret);
        require("razorpay.key-id", razorpayKeyId);
        require("razorpay.key-secret", razorpayKeySecret);
        require("razorpay.webhook-secret", razorpayWebhookSecret);
        require("cors.allowed-origins", corsOrigins);
        requireHttpsOrigins(corsOrigins);
        if (!bedrockEnabled || !routingEnabled || !v2Enabled || !extractionWorkerEnabled)
            throw new IllegalStateException("Production requires Bedrock, AI routing, chat v2, and extraction worker to be enabled");
        if (devMode) throw new IllegalStateException("app.dev-mode must be false in production");
        if (bedrockRegion.isBlank() || !bedrockRegion.equals(usageRegion))
            throw new IllegalStateException("Bedrock and usage accounting regions must match and be configured");
    }

    private static void require(String key, String value) {
        if (value == null || value.isBlank() || value.contains("your_") || value.contains("dummy")
                || value.equalsIgnoreCase("changeme"))
            throw new IllegalStateException(key + " must be configured for the production profile");
    }

    private static void requireHttpsOrigins(String configured) {
        List<String> origins = List.of(configured.split(","));
        if (origins.isEmpty()) throw new IllegalStateException("At least one HTTPS CORS origin is required");
        for (String raw : origins) {
            try {
                URI uri = URI.create(raw.trim());
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                        || uri.getRawPath() != null && !uri.getRawPath().isEmpty()
                        || uri.getQuery() != null || uri.getFragment() != null)
                    throw new IllegalArgumentException();
            } catch (RuntimeException invalid) {
                throw new IllegalStateException("cors.allowed-origins must contain HTTPS origins without paths", invalid);
            }
        }
    }
}
