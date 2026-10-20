package com.hkv.AiTherapy.integration;

import com.hkv.AiTherapy.domain.Conversation;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.dto.request.CreateMemoryRequest;
import com.hkv.AiTherapy.dto.request.MemoryPatchRequest;
import com.hkv.AiTherapy.dto.request.UpdateProfileRequest;
import com.hkv.AiTherapy.repository.ConversationRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.security.JwtTokenProvider;
import com.hkv.AiTherapy.service.memory.MemoryService;
import com.hkv.AiTherapy.service.profile.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ProfileMemoryPersistenceTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("encryption.master-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository users;
    @Autowired ConversationRepository conversations;
    @Autowired ProfileService profiles;
    @Autowired MemoryService memories;
    @Autowired JdbcTemplate jdbc;

    @Test
    void profileMemoryOwnershipPaginationForgetAndResetPersist() {
        User owner = users.save(User.builder().email("owner-" + UUID.randomUUID() + "@example.invalid").build());
        User other = users.save(User.builder().email("other-" + UUID.randomUUID() + "@example.invalid").build());

        assertFalse(profiles.getProfile(owner.getId()).isOnboardingComplete());
        UpdateProfileRequest update = new UpdateProfileRequest();
        update.setPreferredName("Avery");
        update.setAgeRange("25-34");
        update.setGender("nonbinary");
        update.setCountry("IN");
        update.setPersonaId("dr_sarah");
        assertTrue(profiles.updateProfile(owner.getId(), update).isOnboardingComplete());

        Conversation source = conversations.save(Conversation.builder().user(owner).status("completed")
                .sessionSummary("Synthetic past summary").summaryRevision(0L).build());
        CreateMemoryRequest request = new CreateMemoryRequest();
        request.setType("fact");
        request.setTitle("A".repeat(200));
        var saved = memories.addManualMemory(owner.getId(), request);
        UUID memoryId = UUID.fromString(saved.getId());
        assertEquals(1, memories.getMemories(owner.getId(), null, null, 0, 1).getTotal());
        assertNotEquals(request.getTitle(), jdbc.queryForObject(
                "SELECT title FROM long_term_memories WHERE id = ?", String.class, memoryId));

        assertThrows(ResponseStatusException.class, () -> memories.deleteMemory(other.getId(), memoryId));
        MemoryPatchRequest patch = new MemoryPatchRequest();
        patch.setStatus("resolved");
        var edited = memories.patchMemory(owner.getId(), memoryId, "\"" + saved.getVersion() + "\"", patch);
        assertTrue(edited.isResolved());
        assertThrows(ResponseStatusException.class,
                () -> memories.patchMemory(owner.getId(), memoryId, "\"" + saved.getVersion() + "\"", patch));

        memories.forgetMemory(owner.getId(), memoryId, "\"" + edited.getVersion() + "\"");
        assertEquals(0, memories.getMemories(owner.getId(), null, null, 0, 20).getTotal());
        assertEquals(1L, users.findById(owner.getId()).orElseThrow().getContextRevision());
        assertFalse(conversations.findById(source.getId()).orElseThrow().isContextEligible());
        assertNull(conversations.findById(source.getId()).orElseThrow().getSessionSummary());

        assertEquals(2L, profiles.deleteProfile(owner.getId()));
        assertFalse(profiles.getProfile(owner.getId()).isOnboardingComplete());
    }
}
