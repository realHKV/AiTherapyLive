package com.hkv.AiTherapy.security;

import com.hkv.AiTherapy.config.SecurityConfig;
import com.hkv.AiTherapy.config.CorsConfig;
import com.hkv.AiTherapy.controller.AuthController;
import com.hkv.AiTherapy.controller.ChatController;
import com.hkv.AiTherapy.controller.ChatRequestController;
import com.hkv.AiTherapy.controller.DevController;
import com.hkv.AiTherapy.controller.PaymentController;
import com.hkv.AiTherapy.controller.ProfileController;
import com.hkv.AiTherapy.controller.UsageController;
import com.hkv.AiTherapy.dto.response.PurchaseResponse;
import com.hkv.AiTherapy.repository.ConversationRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.service.account.AccountService;
import com.hkv.AiTherapy.service.auth.AuthService;
import com.hkv.AiTherapy.service.auth.TokenService;
import com.hkv.AiTherapy.service.auth.OAuthTicketService;
import com.hkv.AiTherapy.service.chat.ChatService;
import com.hkv.AiTherapy.service.chat.ChatTurnWorkflow;
import com.hkv.AiTherapy.service.chat.ChatStreamHandle;
import com.hkv.AiTherapy.service.job.SummarizationJob;
import com.hkv.AiTherapy.service.memory.MemoryService;
import com.hkv.AiTherapy.service.usage.RedisAdmissionService;
import com.hkv.AiTherapy.service.usage.UsageAccountingService;
import com.hkv.AiTherapy.service.usage.UsageStatus;
import com.hkv.AiTherapy.service.ai.routing.ResolvedGeneration;
import com.hkv.AiTherapy.service.payment.SubscriptionService;
import com.hkv.AiTherapy.service.payment.EntitlementService;
import com.hkv.AiTherapy.service.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, ChatController.class, DevController.class,
        PaymentController.class, ProfileController.class, UsageController.class, ChatRequestController.class})
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RedisRateLimitFilter.class})
@TestPropertySource(properties = {"app.dev-mode=false", "razorpay.webhook-secret=synthetic-webhook-secret"})
class SecurityHttpTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService authService;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private AccountService accountService;
    @MockitoBean private ChatService chatService;
    @MockitoBean private ChatTurnWorkflow chatTurnWorkflow;
    @MockitoBean private SummarizationJob summarizationJob;
    @MockitoBean private ConversationRepository conversationRepository;
    @MockitoBean private ProfileService profileService;
    @MockitoBean private MemoryService memoryService;
    @MockitoBean private SubscriptionService subscriptionService;
    @MockitoBean private EntitlementService entitlementService;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private OAuth2SuccessHandler oAuth2SuccessHandler;
    @MockitoBean private StringRedisTemplate redisTemplate;
    @MockitoBean private RedisAdmissionService redisAdmissionService;
    @MockitoBean private UsageAccountingService usageAccountingService;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private OAuthTicketService oauthTicketService;

    @BeforeEach
    void validSyntheticJwt() {
        when(jwtTokenProvider.validateToken("synthetic-valid-token")).thenReturn(true);
        when(jwtTokenProvider.getJti("synthetic-valid-token")).thenReturn("synthetic-jti");
        when(jwtTokenProvider.getAuthentication("synthetic-valid-token"))
                .thenReturn(new UsernamePasswordAuthenticationToken(USER_ID.toString(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(redisTemplate.hasKey("jwt:denylist:synthetic-jti")).thenReturn(false);
        when(userRepository.existsByIdAndIsActiveTrue(USER_ID)).thenReturn(true);
    }

    @Test
    void anonymousAccountDeletionIsRejected() throws Exception {
        mvc.perform(delete("/auth/account"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(accountService);
    }

    @Test
    void v2StreamIsOptInAuthenticatedAndReturnsReconciliationHeaders() throws Exception {
        UUID sessionId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(10000L);
        when(chatTurnWorkflow.stream(eq(USER_ID), eq(sessionId), eq(idempotencyKey), eq("Synthetic stream")))
                .thenReturn(new ChatStreamHandle(requestId, emitter));

        var result = mvc.perform(post("/chat/sessions/" + sessionId + "/stream")
                        .header("Authorization", "Bearer synthetic-valid-token")
                        .header("X-Chat-Stream-Version", "2")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Synthetic stream\"}"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        assertEquals(requestId, result.getResponse().getHeader("X-Request-Id"));
        assertTrue(result.getResponse().getContentType().startsWith(MediaType.TEXT_EVENT_STREAM_VALUE));
        assertEquals("no-store", result.getResponse().getHeader("Cache-Control"));
        verify(chatTurnWorkflow).stream(USER_ID, sessionId, idempotencyKey, "Synthetic stream");
        emitter.complete();
    }

    @Test
    void corsAllowsV2IdempotencyHeadersAndExposesStreamRequestId() throws Exception {
        mvc.perform(options("/chat/sessions/" + UUID.randomUUID() + "/stream")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key,x-chat-stream-version"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsString("x-chat-stream-version")))
                .andExpect(header().string("Access-Control-Expose-Headers", org.hamcrest.Matchers.containsString("X-Request-Id")));
    }

    @Test
    void authenticatedAccountDeletionUsesTokenIdentity() throws Exception {
        mvc.perform(delete("/auth/account")
                        .header("Authorization", "Bearer synthetic-valid-token"))
                .andExpect(status().isOk());
        verify(accountService).deleteAccount(USER_ID);
    }

    @Test
    void developmentUpgradeRouteIsAbsentInNormalMode() throws Exception {
        mvc.perform(post("/dev/force-pro")
                        .header("Authorization", "Bearer synthetic-valid-token"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(subscriptionService);
    }

    @Test
    void publicAuthRoutesStillReachControllers() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"fixture@example.invalid\",\"password\":\"synthetic-password\"}"))
                .andExpect(status().isOk());
        verify(authService).login(any(), any());

        mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"synthetic-refresh-token\"}"))
                .andExpect(status().isOk());
        verify(authService).refresh(eq("synthetic-refresh-token"), any());
    }

    @Test
    void authenticatedUsageStatusUsesTheServerEntitlement() throws Exception {
        when(entitlementService.isPro(USER_ID)).thenReturn(true);
        when(usageAccountingService.status(USER_ID, ResolvedGeneration.Tier.PRO))
                .thenReturn(new UsageStatus(ResolvedGeneration.Tier.PRO, "test-policy", 10, 500, 100,
                        java.time.Instant.parse("2026-10-21T00:00:00Z")));

        mvc.perform(get("/usage/status").header("Authorization", "Bearer synthetic-valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("PRO"))
                .andExpect(jsonPath("$.data.remainingRequests").value(10))
                .andExpect(jsonPath("$.data.policyId").value("test-policy"));
    }

    @Test
    void authFailureDoesNotExposeInternalExceptionDetails() throws Exception {
        when(authService.login(any(), any())).thenThrow(new IllegalStateException("synthetic-sensitive-detail"));

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"fixture@example.invalid\",\"password\":\"synthetic-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value("Invalid credentials."));
    }

    @Test
    void databaseOutageIsReportedAsUnavailableDuringLogin() throws Exception {
        when(authService.login(any(), any()))
                .thenThrow(new RedisConnectionFailureException("fixture unavailable"));
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"fixture@example.invalid\",\"password\":\"synthetic-password\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("AUTH_UNAVAILABLE"));
    }

    @Test
    void unsignedWebhookIsRejectedWithoutJwt() throws Exception {
        mvc.perform(post("/payment/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"synthetic\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(subscriptionService);
    }

    @Test
    void signedWebhookRemainsPublic() throws Exception {
        String body = "{\"event\":\"synthetic\"}";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("synthetic-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));

        mvc.perform(post("/payment/webhook")
                        .header("X-Razorpay-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void purchaseCreationRequiresAccountAndReturnsServerOrder() throws Exception {
        mvc.perform(post("/payment/purchases")).andExpect(status().isUnauthorized());
        verifyNoInteractions(subscriptionService);
        when(subscriptionService.createPurchase(USER_ID))
                .thenReturn(new PurchaseResponse("purchase-fixture", "order_fixture123", "rzp_test_fixture", 5000, "INR"));
        mvc.perform(post("/payment/purchases")
                        .header("Authorization", "Bearer synthetic-valid-token"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderId").value("order_fixture123"))
                .andExpect(jsonPath("$.data.amountPaise").value(5000));
    }

    @Test
    void chatRequiresAuthentication() throws Exception {
        mvc.perform(post("/chat/sessions"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(chatService);
    }

    @Test
    void usageAdmissionRejectsAThrottledChatSubmission() throws Exception {
        when(redisAdmissionService.admit(eq(USER_ID), any()))
                .thenThrow(new RedisAdmissionService.RateLimitedException(12));

        mvc.perform(post("/chat/sessions").header("Authorization", "Bearer synthetic-valid-token"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "12"))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
        verifyNoInteractions(chatService);
    }

    @Test
    void usageAdmissionFailsClosedWhenAccountingIsUnavailable() throws Exception {
        when(redisAdmissionService.admit(eq(USER_ID), any()))
                .thenThrow(new com.hkv.AiTherapy.service.usage.UsageException(
                        com.hkv.AiTherapy.service.usage.UsageException.Code.ACCOUNTING_UNAVAILABLE));

        mvc.perform(post("/chat/sessions").header("Authorization", "Bearer synthetic-valid-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("ACCOUNTING_UNAVAILABLE"));
        verifyNoInteractions(chatService);
    }

    @Test
    void authenticatedChatRouteUsesTokenIdentity() throws Exception {
        UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        mvc.perform(put("/chat/sessions/{sessionId}/end", sessionId)
                        .header("Authorization", "Bearer synthetic-valid-token"))
                .andExpect(status().isOk());
        verify(chatService).endSession(USER_ID, sessionId);
    }

    @Test
    void disabledAccountAccessTokenIsRejected() throws Exception {
        when(userRepository.existsByIdAndIsActiveTrue(USER_ID)).thenReturn(false);
        mvc.perform(delete("/auth/account")
                        .header("Authorization", "Bearer synthetic-valid-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(accountService);
    }

    @Test
    void redisFailureFailsClosedForAccessToken() throws Exception {
        when(redisTemplate.hasKey("jwt:denylist:synthetic-jti"))
                .thenThrow(new RedisConnectionFailureException("fixture unavailable"));
        mvc.perform(delete("/auth/account")
                        .header("Authorization", "Bearer synthetic-valid-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(accountService);
    }

    @Test
    void oauthExchangeIsPublicAndReplayFailureIsSafe() throws Exception {
        when(oauthTicketService.exchange(eq("synthetic-ticket"), nullable(String.class)))
                .thenThrow(new IllegalArgumentException("fixture internal detail"));
        mvc.perform(post("/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket\":\"synthetic-ticket\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("OAUTH_TICKET_INVALID"));
    }

    @Test
    void oauthSessionCannotAuthenticateApiWithoutBearerToken() throws Exception {
        MockHttpSession session = new MockHttpSession();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        mvc.perform(delete("/auth/account").session(session))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(accountService);
    }
}
