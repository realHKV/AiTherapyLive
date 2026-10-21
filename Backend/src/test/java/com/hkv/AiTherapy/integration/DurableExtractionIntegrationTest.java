package com.hkv.AiTherapy.integration;

import com.hkv.AiTherapy.domain.Conversation;
import com.hkv.AiTherapy.domain.ExtractionJob;
import com.hkv.AiTherapy.domain.Message;
import com.hkv.AiTherapy.domain.TherapyProfile;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.repository.ConversationRepository;
import com.hkv.AiTherapy.repository.ExtractionJobRepository;
import com.hkv.AiTherapy.repository.LongTermMemoryRepository;
import com.hkv.AiTherapy.repository.MessageRepository;
import com.hkv.AiTherapy.repository.ProfileCandidateRepository;
import com.hkv.AiTherapy.repository.TherapyProfileRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.security.JwtTokenProvider;
import com.hkv.AiTherapy.service.account.AccountService;
import com.hkv.AiTherapy.service.ai.routing.CancellationToken;
import com.hkv.AiTherapy.service.ai.routing.GenerationGateway;
import com.hkv.AiTherapy.service.ai.routing.GenerationResult;
import com.hkv.AiTherapy.service.ai.routing.GenerationUsage;
import com.hkv.AiTherapy.service.job.DurableExtractionProcessor;
import com.hkv.AiTherapy.service.job.ExtractionJobQueue;
import com.hkv.AiTherapy.service.chat.ChatService;
import com.hkv.AiTherapy.service.profile.ProfileService;
import com.hkv.AiTherapy.repository.AiRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class DurableExtractionIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("encryption.master-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("ai.usage.region", () -> "ap-south-1");
        registry.add("ai.routing.enabled", () -> "true");
        registry.add("ai.routing.policy-id", () -> "synthetic-extraction-test");
        registry.add("ai.routing.free-model-id", () -> "synthetic-free");
        registry.add("ai.routing.pro-model-id", () -> "synthetic-pro");
        registry.add("ai.routing.summary-model-id", () -> "synthetic-summary");
        registry.add("ai.extraction.worker-enabled", () -> "false");
        registry.add("ai.extraction.max-attempts", () -> "2");
        registry.add("ai.extraction.poll-interval-ms", () -> "60000");
    }

    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean GenerationGateway gateway;
    @Autowired ChatService chat;
    @Autowired UserRepository users;
    @Autowired ConversationRepository conversations;
    @Autowired MessageRepository messages;
    @Autowired ExtractionJobRepository jobs;
    @Autowired ExtractionJobQueue queue;
    @Autowired DurableExtractionProcessor processor;
    @Autowired ProfileCandidateRepository profileCandidates;
    @Autowired LongTermMemoryRepository memories;
    @Autowired TherapyProfileRepository therapyProfiles;
    @Autowired AiRequestRepository requests;
    @Autowired ProfileService profiles;
    @Autowired AccountService accounts;

    @Test
    void sessionEndCommitsOneJobAndSuccessfulExtractionPersistsOnlyEvidenceBackedCandidates() {
        Fixture fixture = completedConversation();
        chat.endSession(fixture.user().getId(), fixture.conversation().getId());
        chat.endSession(fixture.user().getId(), fixture.conversation().getId());
        ExtractionJob job = onlyJob(fixture.user().getId());
        assertEquals("QUEUED", job.getStatus());
        assertEquals(0, job.getSourceRevision());
        assertEquals(1, therapyProfiles.findByUserId(fixture.user().getId()).orElseThrow().getTotalSessions());

        when(gateway.generate(any(), any())).thenReturn(result(validOutput(fixture.message())));
        assertTrue(processor.processOne());

        ExtractionJob completed = jobs.findById(job.getId()).orElseThrow();
        Conversation saved = conversations.findById(fixture.conversation().getId()).orElseThrow();
        assertEquals("COMPLETED", completed.getStatus());
        assertNotNull(saved.getSummarizedAt());
        assertEquals(0L, saved.getSummaryRevision());
        assertEquals("Discussed the user's education goal.", saved.getSessionSummary());
        assertEquals(1, profileCandidates.countByUserIdAndStatus(fixture.user().getId(), "pending"));
        assertEquals(1, memories.countByUserIdAndStatus(fixture.user().getId(), "pending"));
        var request = requests.findAll().stream().filter(value -> value.getUser().getId().equals(fixture.user().getId())).findFirst().orElseThrow();
        assertEquals("COMPLETED", request.getStatus());
        assertEquals("SUMMARY", request.getPurpose());
    }

    @Test
    void invalidEvidenceRetriesThenExhaustsAndOperatorReplayCanRecover() {
        Fixture fixture = completedConversation();
        chat.endSession(fixture.user().getId(), fixture.conversation().getId());
        ExtractionJob job = onlyJob(fixture.user().getId());
        when(gateway.generate(any(), any())).thenReturn(result(invalidEvidenceOutput(fixture.message())));

        assertTrue(processor.processOne());
        ExtractionJob retry = jobs.findById(job.getId()).orElseThrow();
        assertEquals("RETRY_WAIT", retry.getStatus());
        assertNull(conversations.findById(fixture.conversation().getId()).orElseThrow().getSummarizedAt());
        assertEquals(0, memories.countByUserIdAndStatus(fixture.user().getId(), "pending"));

        retry.setNextAttemptAt(Instant.now().minusSeconds(1));
        jobs.saveAndFlush(retry);
        assertTrue(processor.processOne());
        assertEquals("FAILED", jobs.findById(job.getId()).orElseThrow().getStatus());
        assertTrue(queue.replayFailed(job.getId()));
        assertFalse(queue.replayFailed(job.getId()));
        when(gateway.generate(any(), any())).thenReturn(result(validOutput(fixture.message())));
        assertTrue(processor.processOne());

        assertEquals("COMPLETED", jobs.findById(job.getId()).orElseThrow().getStatus());
        assertEquals(1, memories.countByUserIdAndStatus(fixture.user().getId(), "pending"));
        assertEquals(3, requests.findAll().stream().filter(request -> request.getUser().getId().equals(fixture.user().getId())).count());
    }

    @Test
    void expiredWorkerLeaseCanBeClaimedAfterAProcessCrash() throws Exception {
        Fixture fixture = completedConversation();
        chat.endSession(fixture.user().getId(), fixture.conversation().getId());
        ExtractionJob job = onlyJob(fixture.user().getId());
        UUID firstWorker = UUID.randomUUID();
        UUID secondWorker = UUID.randomUUID();

        assertEquals(job.getId(), queue.claimOne(firstWorker, Duration.ofMillis(1)));
        Thread.sleep(10);
        assertEquals(job.getId(), queue.claimOne(secondWorker, Duration.ofMinutes(1)));
        ExtractionJob reclaimed = jobs.findById(job.getId()).orElseThrow();
        assertEquals(2, reclaimed.getAttemptCount());
        assertEquals(secondWorker, reclaimed.getClaimedBy());
    }

    @Test
    void profileResetDuringProviderCallDiscardsCandidatesAndSummary() {
        Fixture fixture = completedConversation();
        chat.endSession(fixture.user().getId(), fixture.conversation().getId());
        ExtractionJob job = onlyJob(fixture.user().getId());
        when(gateway.generate(any(), any())).thenAnswer(invocation -> {
            profiles.deleteProfile(fixture.user().getId());
            return result(validOutput(fixture.message()));
        });

        assertTrue(processor.processOne());
        assertEquals("SUPERSEDED", jobs.findById(job.getId()).orElseThrow().getStatus());
        assertNull(conversations.findById(fixture.conversation().getId()).orElseThrow().getSessionSummary());
        assertEquals(0, profileCandidates.countByUserIdAndStatus(fixture.user().getId(), "pending"));
        assertEquals(0, memories.countByUserIdAndStatus(fixture.user().getId(), "pending"));
    }

    @Test
    void accountDeletionDuringProviderCallCannotRecreateDeletedRecords() {
        Fixture fixture = completedConversation();
        chat.endSession(fixture.user().getId(), fixture.conversation().getId());
        when(gateway.generate(any(), any())).thenAnswer(invocation -> {
            accounts.deleteAccount(fixture.user().getId());
            return result(validOutput(fixture.message()));
        });

        assertTrue(processor.processOne());
        assertFalse(users.existsById(fixture.user().getId()));
        assertFalse(conversations.existsById(fixture.conversation().getId()));
        assertEquals(0, jobs.findAll().stream().filter(job -> job.getUser().getId().equals(fixture.user().getId())).count());
    }

    private Fixture completedConversation() {
        User user = users.saveAndFlush(User.builder().email("extract-" + UUID.randomUUID() + "@example.invalid").build());
        therapyProfiles.saveAndFlush(TherapyProfile.builder().user(user).totalSessions(0).build());
        Conversation conversation = conversations.saveAndFlush(Conversation.builder().user(user).status("active").tokenCount(0).build());
        Message message = messages.saveAndFlush(Message.builder().conversation(conversation).role("user")
                .content("I am working toward finishing my nursing course.").tokenCount(12).build());
        return new Fixture(user, conversation, message);
    }

    private ExtractionJob onlyJob(UUID userId) {
        List<ExtractionJob> owned = jobs.findAll().stream().filter(job -> job.getUser().getId().equals(userId)).toList();
        assertEquals(1, owned.size());
        return owned.getFirst();
    }

    private static String validOutput(Message message) {
        return """
                {"summary":"Discussed the user's education goal.",
                 "profileCandidates":[{"field":"name","value":"Alex","sourceMessageId":"%s","evidence":"nursing course"}],
                 "traits":[],
                 "memories":[{"type":"goal","title":"Finish nursing course","detail":"A stated education goal.",
                 "importance":7,"followUpAt":"2027-04-01","sourceMessageId":"%s","evidence":"working toward finishing my nursing course"}]}
                """.formatted(message.getId(), message.getId());
    }

    private static String invalidEvidenceOutput(Message message) {
        return """
                {"summary":"Discussed an education goal.","profileCandidates":[],"traits":[],
                 "memories":[{"type":"fact","title":"Unsupported claim","importance":5,
                 "sourceMessageId":"%s","evidence":"I own a private island"}]}
                """.formatted(message.getId());
    }

    private static GenerationResult result(String text) {
        return new GenerationResult(text, GenerationResult.Finish.COMPLETED, new GenerationUsage(100, 80),
                "synthetic-extraction-test", "synthetic-summary");
    }

    private record Fixture(User user, Conversation conversation, Message message) { }
}
