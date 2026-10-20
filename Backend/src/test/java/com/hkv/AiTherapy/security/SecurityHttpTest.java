package com.hkv.AiTherapy.security;

import com.hkv.AiTherapy.config.SecurityConfig;
import com.hkv.AiTherapy.config.CorsConfig;
import com.hkv.AiTherapy.controller.AuthController;
import com.hkv.AiTherapy.controller.ChatController;
import com.hkv.AiTherapy.controller.DevController;
import com.hkv.AiTherapy.controller.PaymentController;
import com.hkv.AiTherapy.controller.ProfileController;
import com.hkv.AiTherapy.repository.ConversationRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.service.account.AccountService;
import com.hkv.AiTherapy.service.auth.AuthService;
import com.hkv.AiTherapy.service.auth.TokenService;
import com.hkv.AiTherapy.service.auth.OAuthTicketService;
import com.hkv.AiTherapy.service.chat.ChatService;
import com.hkv.AiTherapy.service.job.SummarizationJob;
import com.hkv.AiTherapy.service.memory.MemoryService;
import com.hkv.AiTherapy.service.payment.SubscriptionService;
import com.hkv.AiTherapy.service.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, ChatController.class, DevController.class,
        PaymentController.class, ProfileController.class})
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, RedisRateLimitFilter.class})
@TestPropertySource(properties = {"app.dev-mode=false", "razorpay.webhook-secret=synthetic-webhook-secret"})
class SecurityHttpTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService authService;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private AccountService accountService;
    @MockitoBean private ChatService chatService;
    @MockitoBean private SummarizationJob summarizationJob;
    @MockitoBean private ConversationRepository conversationRepository;
    @MockitoBean private ProfileService profileService;
    @MockitoBean private MemoryService memoryService;
    @MockitoBean private SubscriptionService subscriptionService;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private OAuth2SuccessHandler oAuth2SuccessHandler;
    @MockitoBean private StringRedisTemplate redisTemplate;
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
    void chatRequiresAuthentication() throws Exception {
        mvc.perform(post("/chat/sessions"))
                .andExpect(status().isUnauthorized());
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
