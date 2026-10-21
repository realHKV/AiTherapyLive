package com.hkv.AiTherapy.integration;

import com.hkv.AiTherapy.encryption.EncryptionService;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
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
        assertEquals(9, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);

        EncryptionService encryption = new EncryptionService();
        ReflectionTestUtils.setField(encryption, "masterKeyBase64",
                Base64.getEncoder().encodeToString(new byte[32]));
        encryption.init();
        EncryptionService restoreReader = new EncryptionService();
        ReflectionTestUtils.setField(restoreReader, "masterKeyBase64",
                Base64.getEncoder().encodeToString(new byte[32]));
        restoreReader.init();

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
                    assertEquals(text, restoreReader.decrypt(stored));
                }
            }
            String longTitle = "Private milestone 🌱 ".repeat(30);
            try (var insert = connection.prepareStatement(
                    "INSERT INTO long_term_memories (user_id, memory_type, title, status, source_kind, confirmed_at) VALUES (?, ?, ?, ?, ?, now())")) {
                insert.setObject(1, userId);
                insert.setString(2, "fact");
                insert.setString(3, encryption.encrypt(longTitle));
                insert.setString(4, "active");
                insert.setString(5, "user_saved");
                assertEquals(1, insert.executeUpdate());
            }
            try (var select = connection.prepareStatement("SELECT title FROM long_term_memories WHERE user_id = ?")) {
                select.setObject(1, userId);
                try (var result = select.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(longTitle, restoreReader.decrypt(result.getString(1)));
                }
            }
        }
    }

    @Test
    void upgradesExistingV5DataWithoutChangingIdsOrCiphertext() throws Exception {
        String schema = "upgrade_fixture";
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        Flyway throughV5 = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .defaultSchema(schema)
                .schemas(schema)
                .target(MigrationVersion.fromVersion("5"))
                .load();
        assertEquals(5, throughV5.migrate().migrationsExecuted);

        UUID userId = UUID.randomUUID();
        UUID memoryId = UUID.randomUUID();
        String legacyCiphertext = "legacy-ciphertext-fixture";
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            try (var insert = connection.prepareStatement(
                    "INSERT INTO upgrade_fixture.users (id, email) VALUES (?, ?)")) {
                insert.setObject(1, userId);
                insert.setString(2, "upgrade-" + userId + "@example.invalid");
                assertEquals(1, insert.executeUpdate());
            }
            try (var insert = connection.prepareStatement(
                    "INSERT INTO upgrade_fixture.long_term_memories (id, user_id, memory_type, title) VALUES (?, ?, ?, ?)")) {
                insert.setObject(1, memoryId);
                insert.setObject(2, userId);
                insert.setString(3, "fact");
                insert.setString(4, legacyCiphertext);
                assertEquals(1, insert.executeUpdate());
            }
        }

        Flyway upgrade = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .defaultSchema(schema)
                .schemas(schema)
                .load();
        assertEquals(4, upgrade.migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var select = connection.prepareStatement(
                     "SELECT title, status, source_kind FROM upgrade_fixture.long_term_memories WHERE id = ?")) {
            select.setObject(1, memoryId);
            try (var result = select.executeQuery()) {
                assertTrue(result.next());
                assertEquals(legacyCiphertext, result.getString(1));
                assertEquals("active", result.getString(2));
                assertEquals("user_saved", result.getString(3));
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
