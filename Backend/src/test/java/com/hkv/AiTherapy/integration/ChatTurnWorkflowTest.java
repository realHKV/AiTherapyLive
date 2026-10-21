package com.hkv.AiTherapy.integration;

import com.hkv.AiTherapy.domain.Conversation;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.dto.response.ConversationResponse;
import com.hkv.AiTherapy.repository.AiRequestRepository;
import com.hkv.AiTherapy.repository.ChatTurnRepository;
import com.hkv.AiTherapy.repository.ConversationRepository;
import com.hkv.AiTherapy.repository.MessageRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.security.JwtTokenProvider;
import com.hkv.AiTherapy.service.ai.routing.CancellationToken;
import com.hkv.AiTherapy.service.ai.routing.GenerationEvent;
import com.hkv.AiTherapy.service.ai.routing.GenerationFailure;
import com.hkv.AiTherapy.service.ai.routing.GenerationGateway;
import com.hkv.AiTherapy.service.ai.routing.GenerationResult;
import com.hkv.AiTherapy.service.ai.routing.GenerationUsage;
import com.hkv.AiTherapy.service.chat.ChatService;
import com.hkv.AiTherapy.service.chat.ChatTurnWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ChatTurnWorkflowTest {
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
        registry.add("ai.usage.region", () -> "ap-south-1");
        registry.add("ai.routing.enabled", () -> "true");
        registry.add("ai.routing.policy-id", () -> "synthetic-chat-test");
        registry.add("ai.routing.free-model-id", () -> "synthetic-free");
        registry.add("ai.routing.pro-model-id", () -> "synthetic-pro");
        registry.add("ai.routing.summary-model-id", () -> "synthetic-summary");
        registry.add("ai.chat.v2.enabled", () -> "true");
        registry.add("ai.chat.v2.first-token-timeout", () -> "200ms");
        registry.add("ai.chat.v2.total-timeout", () -> "1s");
        registry.add("ai.chat.v2.stream-timeout", () -> "2s");
        registry.add("ai.chat.v2.max-output-tokens", () -> "128");
    }

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean GenerationGateway gateway;
    @LocalServerPort int port;
    @Autowired ChatTurnWorkflow workflow;
    @Autowired ChatService chatService;
    @Autowired UserRepository users;
    @Autowired ConversationRepository conversations;
    @Autowired MessageRepository messages;
    @Autowired ChatTurnRepository turns;
    @Autowired AiRequestRepository requests;

    @BeforeEach
    void defaultGatewayBehavior() {
        when(gateway.generate(any(), any())).thenAnswer(invocation -> result("Synthetic routed answer."));
        doAnswer(invocation -> {
            var events = (java.util.function.Consumer<GenerationEvent>) invocation.getArgument(2);
            events.accept(new GenerationEvent.Started("synthetic-chat-test", "synthetic-free"));
            events.accept(new GenerationEvent.Delta("Synthetic "));
            events.accept(new GenerationEvent.Delta("answer 🙂"));
            events.accept(new GenerationEvent.Completed(result("Synthetic answer 🙂")));
            return null;
        }).when(gateway).stream(any(), any(), any());
    }

    @Test
    void repeatedSubmissionReturnsOnePersistedTurnAndRejectsChangedPayload() {
        User user = createUser();
        Conversation conversation = createConversation(user);
        String key = UUID.randomUUID().toString();

        var first = workflow.submit(user.getId(), conversation.getId(), key, "Synthetic Unicode message 🌱");
        var replay = workflow.submit(user.getId(), conversation.getId(), key, "Synthetic Unicode message 🌱");

        assertEquals(first.getId(), replay.getId());
        assertEquals(2, messages.countByConversationId(conversation.getId()));
        assertEquals("COMPLETED", turns.findAll().stream().filter(turn -> turn.getUser().getId().equals(user.getId()))
                .findFirst().orElseThrow().getStatus());
        assertEquals(1, requests.findAll().stream().filter(request -> request.getUser().getId().equals(user.getId())).count());
        verify(gateway, times(1)).generate(any(), any());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> workflow.submit(user.getId(), conversation.getId(), key, "Changed payload")).getStatusCode().value());
        assertEquals(2, messages.countByConversationId(conversation.getId()));
    }

    @Test
    void streamProviderFailureAfterPartialTextDoesNotPersistAnAssistantAnswer() throws Exception {
        User user = createUser();
        Conversation conversation = createConversation(user);
        CountDownLatch terminal = new CountDownLatch(1);
        doAnswer(invocation -> {
            var events = (java.util.function.Consumer<GenerationEvent>) invocation.getArgument(2);
            events.accept(new GenerationEvent.Delta("Partial Unicode 🌱"));
            events.accept(new GenerationEvent.Failed(new GenerationFailure(GenerationFailure.Code.THROTTLED, true)));
            terminal.countDown();
            return null;
        }).when(gateway).stream(any(), any(), any());

        workflow.stream(user.getId(), conversation.getId(), UUID.randomUUID().toString(), "Synthetic stream prompt");

        assertTrue(terminal.await(2, TimeUnit.SECONDS));
        awaitStatus(user.getId(), "FAILED");
        assertEquals(1, messages.countByConversationId(conversation.getId()));
    }

    @Test
    void realHttpStreamFramesNamedEventsAndPreservesUnicodeDeltas() throws Exception {
        User user = createUser();
        Conversation conversation = createConversation(user);
        when(jwtTokenProvider.validateToken("synthetic-stream-token")).thenReturn(true);
        when(jwtTokenProvider.getJti("synthetic-stream-token")).thenReturn("synthetic-stream-jti");
        when(jwtTokenProvider.getAuthentication("synthetic-stream-token")).thenReturn(
                new UsernamePasswordAuthenticationToken(user.getId().toString(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        String key = UUID.randomUUID().toString();

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port
                        + "/api/v1/chat/sessions/" + conversation.getId() + "/stream"))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer synthetic-stream-token")
                .header("X-Chat-Stream-Version", "2")
                .header("Idempotency-Key", key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"content\":\"Stream 🌱\"}"))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null));
        assertTrue(response.headers().firstValue("X-Request-Id").isPresent());
        assertTrue(response.body().contains("event:start\n"), response.body());
        assertTrue(response.body().contains("event:delta\n"), response.body());
        assertTrue(response.body().contains("Synthetic "), response.body());
        assertTrue(response.body().contains("answer 🙂"), response.body());
        assertTrue(response.body().contains("event:complete\n"), response.body());
        assertFalse(response.body().contains("event:error\n"), response.body());
        awaitStatus(user.getId(), "COMPLETED");
    }

    @Test
    void firstTokenDeadlineCancelsProviderAndLeavesDurableFailure() throws Exception {
        User user = createUser();
        Conversation conversation = createConversation(user);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        doAnswer(invocation -> {
            CancellationToken token = invocation.getArgument(1);
            var events = (java.util.function.Consumer<GenerationEvent>) invocation.getArgument(2);
            token.onCancel(cancelled::countDown);
            entered.countDown();
            cancelled.await(1, TimeUnit.SECONDS);
            events.accept(new GenerationEvent.Failed(new GenerationFailure(GenerationFailure.Code.CANCELLED, false)));
            return null;
        }).when(gateway).stream(any(), any(), any());

        workflow.stream(user.getId(), conversation.getId(), UUID.randomUUID().toString(), "Wait for first token");

        assertTrue(entered.await(1, TimeUnit.SECONDS));
        assertTrue(cancelled.await(1, TimeUnit.SECONDS));
        awaitStatus(user.getId(), "FAILED");
        var turn = turns.findAll().stream().filter(value -> value.getUser().getId().equals(user.getId())).findFirst().orElseThrow();
        assertEquals("GENERATION_TIMEOUT", turn.getFailureCode());
        assertNull(turn.getAssistantMessage());
    }

    @Test
    void endingSessionCancelsActiveGenerationAndPreventsLateAssistantPersistence() throws Exception {
        User user = createUser();
        Conversation conversation = createConversation(user);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        doAnswer(invocation -> {
            CancellationToken token = invocation.getArgument(1);
            var events = (java.util.function.Consumer<GenerationEvent>) invocation.getArgument(2);
            token.onCancel(cancelled::countDown);
            entered.countDown();
            cancelled.await(1, TimeUnit.SECONDS);
            events.accept(new GenerationEvent.Failed(new GenerationFailure(GenerationFailure.Code.CANCELLED, false)));
            return null;
        }).when(gateway).stream(any(), any(), any());

        workflow.stream(user.getId(), conversation.getId(), UUID.randomUUID().toString(), "End while generating");
        assertTrue(entered.await(1, TimeUnit.SECONDS));
        chatService.endSession(user.getId(), conversation.getId());
        workflow.cancelConversation(user.getId(), conversation.getId());

        assertTrue(cancelled.await(1, TimeUnit.SECONDS));
        awaitStatus(user.getId(), "CANCELLED");
        assertEquals(1, messages.countByConversationId(conversation.getId()));
    }

    @Test
    void startingAgainWithNewKeyReusesActiveSessionWithoutAnotherGreetingCharge() {
        User user = createUser();
        String firstKey = UUID.randomUUID().toString();
        String secondKey = UUID.randomUUID().toString();

        ConversationResponse first = workflow.startSession(user.getId(), firstKey);
        ConversationResponse replay = workflow.startSession(user.getId(), firstKey);
        ConversationResponse reload = workflow.startSession(user.getId(), secondKey);

        assertEquals(first.getId(), replay.getId());
        assertEquals(first.getId(), reload.getId());
        assertEquals(1, messages.countByConversationId(UUID.fromString(first.getId())));
        assertEquals(1, requests.findAll().stream().filter(request -> request.getUser().getId().equals(user.getId())).count());
        verify(gateway, times(1)).generate(any(), any());
    }

    private User createUser() {
        return users.save(User.builder().email("chat-" + UUID.randomUUID() + "@example.invalid").build());
    }
    private Conversation createConversation(User user) {
        return conversations.saveAndFlush(Conversation.builder().user(user).status("active").tokenCount(0).build());
    }
    private void awaitStatus(UUID userId, String expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            var turn = turns.findAll().stream().filter(value -> value.getUser().getId().equals(userId)).findFirst().orElse(null);
            if (turn != null && expected.equals(turn.getStatus())) return;
            Thread.sleep(10);
        }
        fail("Chat request did not reach " + expected);
    }
    private static GenerationResult result(String text) {
        return new GenerationResult(text, GenerationResult.Finish.COMPLETED, GenerationUsage.unknown(),
                "synthetic-chat-test", "synthetic-free");
    }
}
