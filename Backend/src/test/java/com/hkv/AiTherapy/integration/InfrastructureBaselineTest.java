package com.hkv.AiTherapy.integration;

import com.hkv.AiTherapy.encryption.EncryptionService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class InfrastructureBaselineTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Test
    void flywayMigratesAndEncryptedFieldPersistsAsCiphertext() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();
        assertEquals(4, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);

        EncryptionService encryption = new EncryptionService();
        ReflectionTestUtils.setField(encryption, "masterKeyBase64",
                Base64.getEncoder().encodeToString(new byte[32]));
        encryption.init();

        UUID userId = UUID.randomUUID();
        String text = "Synthetic private note 🌱";
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            try (var insert = connection.prepareStatement("INSERT INTO users (id, email) VALUES (?, ?)")) {
                insert.setObject(1, userId);
                insert.setString(2, "fixture-" + userId + "@example.invalid");
                assertEquals(1, insert.executeUpdate());
            }
            try (var insert = connection.prepareStatement(
                    "INSERT INTO therapy_profiles (user_id, preferred_name) VALUES (?, ?)")) {
                insert.setObject(1, userId);
                insert.setString(2, encryption.encrypt(text));
                assertEquals(1, insert.executeUpdate());
            }
            try (var select = connection.prepareStatement(
                    "SELECT preferred_name FROM therapy_profiles WHERE user_id = ?")) {
                select.setObject(1, userId);
                try (var result = select.executeQuery()) {
                    assertTrue(result.next());
                    String stored = result.getString(1);
                    assertNotEquals(text, stored);
                    assertEquals(text, encryption.decrypt(stored));
                }
            }
        }
    }

    @Test
    void redisIsReachable() {
        var config = new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379));
        var factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        try (var connection = factory.getConnection()) {
            assertEquals("PONG", connection.ping());
        } finally {
            factory.destroy();
        }
    }
}
