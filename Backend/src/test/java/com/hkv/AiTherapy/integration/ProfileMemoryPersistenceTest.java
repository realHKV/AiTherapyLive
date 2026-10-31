package com.hkv.AiTherapy.integration;

import com.hkv.AiTherapy.domain.Conversation;
import com.hkv.AiTherapy.domain.LongTermMemory;
import com.hkv.AiTherapy.domain.Purchase;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.dto.request.CreateMemoryRequest;
import com.hkv.AiTherapy.dto.request.MemoryPatchRequest;
import com.hkv.AiTherapy.dto.request.UpdateProfileRequest;
import com.hkv.AiTherapy.repository.ConversationRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.repository.LongTermMemoryRepository;
import com.hkv.AiTherapy.repository.PaymentRepository;
import com.hkv.AiTherapy.repository.PurchaseRepository;
import com.hkv.AiTherapy.repository.UserSubscriptionRepository;
import com.hkv.AiTherapy.security.JwtTokenProvider;
import com.hkv.AiTherapy.service.memory.MemoryService;
import com.hkv.AiTherapy.service.profile.ProfileService;
import com.hkv.AiTherapy.service.usage.UsageAccountingService;
import com.hkv.AiTherapy.service.usage.UsageException;
import com.hkv.AiTherapy.service.ai.routing.GenerationMessage;
import com.hkv.AiTherapy.service.ai.routing.GenerationPurpose;
import com.hkv.AiTherapy.service.ai.routing.ResolvedGeneration;
import com.hkv.AiTherapy.service.ai.PromptBuilder;
import com.hkv.AiTherapy.service.payment.PaymentCaptureService;
import com.hkv.AiTherapy.service.payment.RazorpayClient;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.UUID;
import java.util.List;

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
        registry.add("ai.usage.free.daily-requests", () -> "2");
        registry.add("ai.usage.free.daily-tokens", () -> "1000");
    }

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository users;
    @Autowired ConversationRepository conversations;
    @Autowired ProfileService profiles;
    @Autowired MemoryService memories;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsageAccountingService usage;
    @Autowired LongTermMemoryRepository memoryRepository;
    @Autowired PromptBuilder promptBuilder;
    @Autowired PaymentCaptureService paymentCaptureService;
    @Autowired PurchaseRepository purchases;
    @Autowired PaymentRepository paymentRepository;
    @Autowired UserSubscriptionRepository subscriptions;

    @Test
    void promptRetrievalUsesOnlyConfirmedCurrentSourceEligibleMemories() {
        User user = users.save(User.builder().email("memory-context-" + UUID.randomUUID() + "@example.invalid").build());
        UpdateProfileRequest profile = new UpdateProfileRequest();
        profile.setPreferredName("Synthetic");
        profiles.updateProfile(user.getId(), profile);

        CreateMemoryRequest confirmed = new CreateMemoryRequest();
        confirmed.setType("goal");
        confirmed.setTitle("Work anxiety coping plan");
        confirmed.setDetail("Prepare for a manager conversation");
        confirmed.setImportance(5);
        memories.addManualMemory(user.getId(), confirmed);

        memoryRepository.save(LongTermMemory.builder().user(user).memoryType("goal")
                .title("Unconfirmed sensitive candidate").detail("Must never enter a prompt")
                .importance(10).status("active").sourceKind("ai_inferred")
                .sourceRevision(user.getContextRevision()).build());

        Conversation suppressed = conversations.save(Conversation.builder().user(user).status("completed")
                .contextEligible(false).build());
        memoryRepository.save(LongTermMemory.builder().user(user).sourceConversation(suppressed)
                .memoryType("goal").title("Forgotten manager concern").importance(10)
                .status("active").sourceKind("user_saved").confirmedAt(java.time.Instant.now())
                .sourceRevision(user.getContextRevision()).build());

        String prompt = promptBuilder.buildSystemPrompt(user.getId(), "work anxiety", 600);
        assertTrue(prompt.contains("Work anxiety coping plan"));
        assertFalse(prompt.contains("Unconfirmed sensitive candidate"));
        assertFalse(prompt.contains("Must never enter a prompt"));
        assertFalse(prompt.contains("Forgotten manager concern"));
    }

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

    @Test
    void durableUsageReservationIsIdempotentAndCannotOverspend() {
        User user = users.save(User.builder().email("usage-" + UUID.randomUUID() + "@example.invalid").build());
        var generation = new ResolvedGeneration(user.getId(), GenerationPurpose.CHAT,
                ResolvedGeneration.Tier.FREE, "route-policy", "synthetic-model",
                List.of(new GenerationMessage(GenerationMessage.Role.USER, "Synthetic request")), 100);
        var first = usage.reserve(generation, "request-key-0001", 50);
        var duplicate = usage.reserve(generation, "request-key-0001", 50);
        assertEquals(first.requestId(), duplicate.requestId());
        assertTrue(duplicate.existing());
        var second = usage.reserve(generation, "request-key-0002", 50);
        assertThrows(UsageException.class, () -> usage.reserve(generation, "request-key-0003", 50));

        usage.startAttempt(first.requestId());
        usage.settle(first.requestId(), 10, 20, "COMPLETED", null);
        var status = usage.status(user.getId(), ResolvedGeneration.Tier.FREE);
        assertEquals(0, status.remainingRequests());
        assertEquals(150, status.reservedTokens());
        assertEquals(820, status.remainingTokens());
        Integer attempts = jdbc.queryForObject("SELECT provider_attempts FROM ai_requests WHERE id = ?", Integer.class, first.requestId());
        assertEquals(1, attempts);
        String attemptStatus = jdbc.queryForObject("SELECT status FROM ai_attempts WHERE request_id = ?", String.class, first.requestId());
        assertEquals("COMPLETED", attemptStatus);
        assertNotNull(second.requestId());
    }

    @Test
    void concurrentReservationsCannotExceedTheDurableDailyLimit() throws Exception {
        User user = users.save(User.builder().email("usage-concurrent-" + UUID.randomUUID() + "@example.invalid").build());
        var generation = new ResolvedGeneration(user.getId(), GenerationPurpose.CHAT,
                ResolvedGeneration.Tier.FREE, "route-policy", "synthetic-model",
                List.of(new GenerationMessage(GenerationMessage.Role.USER, "Synthetic request")), 100);
        CyclicBarrier start = new CyclicBarrier(3);
        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            List<Future<Boolean>> results = executor.invokeAll(List.of(
                    reserveAfterBarrier(start, generation, "concurrent-key-01"),
                    reserveAfterBarrier(start, generation, "concurrent-key-02"),
                    reserveAfterBarrier(start, generation, "concurrent-key-03")));
            long accepted = 0;
            for (Future<Boolean> result : results) if (result.get()) accepted++;
            assertEquals(2, accepted);
        }
    }

    @Test
    void concurrentDuplicatePaymentCallbacksGrantOneSubscriptionPeriod() throws Exception {
        User user = users.save(User.builder().email("payment-race-" + UUID.randomUUID() + "@example.invalid").build());
        String orderId = "order_" + UUID.randomUUID().toString().replace("-", "");
        String paymentId = "pay_" + UUID.randomUUID().toString().replace("-", "");
        purchases.saveAndFlush(Purchase.builder().id(UUID.randomUUID()).user(user).razorpayOrderId(orderId)
                .expectedAmountPaise(5000).currency("INR").status("PENDING").build());
        RazorpayClient.PaymentDetails details = new RazorpayClient.PaymentDetails(
                paymentId, orderId, 5000, "INR", "captured");

        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<UUID>> results = executor.invokeAll(List.of(
                    captureAfterBarrier(start, details, user.getId()),
                    captureAfterBarrier(start, details, user.getId())));
            assertEquals(user.getId(), results.get(0).get());
            assertEquals(user.getId(), results.get(1).get());
        }

        assertEquals(1, paymentRepository.findAll().stream()
                .filter(payment -> paymentId.equals(payment.getRazorpayPaymentId())).count());
        var subscription = subscriptions.findByUserId(user.getId()).orElseThrow();
        assertEquals("PRO", subscription.getTier());
        assertTrue(subscription.getProExpiresAt().isAfter(java.time.Instant.now().plus(29, java.time.temporal.ChronoUnit.DAYS)));
        assertTrue(subscription.getProExpiresAt().isBefore(java.time.Instant.now().plus(31, java.time.temporal.ChronoUnit.DAYS)));
    }

    @Test
    void onePaymentIdCannotGrantTwoPurchases() {
        User firstUser = users.save(User.builder().email("payment-owner-a-" + UUID.randomUUID() + "@example.invalid").build());
        User secondUser = users.save(User.builder().email("payment-owner-b-" + UUID.randomUUID() + "@example.invalid").build());
        String firstOrder = "order_" + UUID.randomUUID().toString().replace("-", "");
        String secondOrder = "order_" + UUID.randomUUID().toString().replace("-", "");
        String paymentId = "pay_" + UUID.randomUUID().toString().replace("-", "");
        purchases.saveAndFlush(Purchase.builder().id(UUID.randomUUID()).user(firstUser).razorpayOrderId(firstOrder)
                .expectedAmountPaise(5000).currency("INR").status("PENDING").build());
        purchases.saveAndFlush(Purchase.builder().id(UUID.randomUUID()).user(secondUser).razorpayOrderId(secondOrder)
                .expectedAmountPaise(5000).currency("INR").status("PENDING").build());

        paymentCaptureService.capture(new RazorpayClient.PaymentDetails(paymentId, firstOrder, 5000, "INR", "captured"),
                firstUser.getId());
        assertThrows(IllegalArgumentException.class,
                () -> paymentCaptureService.capture(new RazorpayClient.PaymentDetails(paymentId, secondOrder,
                        5000, "INR", "captured"), secondUser.getId()));

        assertEquals("PRO", subscriptions.findByUserId(firstUser.getId()).orElseThrow().getTier());
        assertTrue(subscriptions.findByUserId(secondUser.getId()).isEmpty());
        assertEquals(1, paymentRepository.findAll().stream()
                .filter(payment -> paymentId.equals(payment.getRazorpayPaymentId())).count());
    }

    private Callable<UUID> captureAfterBarrier(CyclicBarrier start, RazorpayClient.PaymentDetails details, UUID userId) {
        return () -> {
            start.await();
            return paymentCaptureService.capture(details, userId);
        };
    }

    private Callable<Boolean> reserveAfterBarrier(CyclicBarrier start, ResolvedGeneration generation, String key) {
        return () -> {
            start.await();
            try {
                usage.reserve(generation, key, 50);
                return true;
            } catch (UsageException exception) {
                assertEquals(UsageException.Code.USER_LIMIT, exception.code());
                return false;
            }
        };
    }
}
