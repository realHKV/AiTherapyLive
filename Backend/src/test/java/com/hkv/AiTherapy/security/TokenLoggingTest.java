package com.hkv.AiTherapy.security;

import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.repository.TherapyProfileRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.service.auth.OAuthTicketService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class TokenLoggingTest {

    @Test
    void oauthCallbackDoesNotPrintAccessToken(CapturedOutput output) throws Exception {
        String sensitiveToken = "synthetic-sensitive-token-marker";
        JwtTokenProvider tokens = mock(JwtTokenProvider.class);
        UserRepository users = mock(UserRepository.class);
        TherapyProfileRepository profiles = mock(TherapyProfileRepository.class);
        OAuth2User oauthUser = mock(OAuth2User.class);
        User user = User.builder().id(UUID.randomUUID()).email("fixture@example.invalid")
                .oauthProvider("google").oauthSubject("synthetic-subject").isActive(true).build();
        when(oauthUser.getAttribute("email")).thenReturn("fixture@example.invalid");
        when(oauthUser.getAttribute("name")).thenReturn("Fixture");
        when(oauthUser.getAttribute("sub")).thenReturn("synthetic-subject");
        when(oauthUser.getAttribute("email_verified")).thenReturn(true);
        when(users.findByOauthProviderAndOauthSubject("google", "synthetic-subject"))
                .thenReturn(Optional.of(user));
        when(tokens.generateAccessToken(user)).thenReturn(sensitiveToken);
        when(profiles.existsByUserId(user.getId())).thenReturn(true);

        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(tokens, users, profiles, mock(OAuthTicketService.class));
        ReflectionTestUtils.setField(handler, "corsAllowedOrigins", "http://localhost:5173");
        var response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                new UsernamePasswordAuthenticationToken(oauthUser, null));

        assertEquals(302, response.getStatus());
        assertTrue(response.getRedirectedUrl().contains(sensitiveToken));
        assertFalse(output.getOut().contains(sensitiveToken));
        assertFalse(output.getErr().contains(sensitiveToken));
    }

    @Test
    void invalidJwtErrorDoesNotPrintToken(CapturedOutput output) throws Exception {
        String sensitiveToken = "synthetic-sensitive-jwt-marker";
        JwtTokenProvider tokens = mock(JwtTokenProvider.class);
        when(tokens.validateToken(sensitiveToken)).thenThrow(new IllegalArgumentException(sensitiveToken));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens, mock(StringRedisTemplate.class), mock(UserRepository.class));
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + sensitiveToken);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        assertFalse(output.getOut().contains(sensitiveToken));
        assertFalse(output.getErr().contains(sensitiveToken));
    }

    @Test
    void optInOauthCallbackRedirectsWithOneTimeTicketOnly() throws Exception {
        JwtTokenProvider tokens = mock(JwtTokenProvider.class);
        UserRepository users = mock(UserRepository.class);
        TherapyProfileRepository profiles = mock(TherapyProfileRepository.class);
        OAuthTicketService tickets = mock(OAuthTicketService.class);
        OAuth2User identity = mock(OAuth2User.class);
        User user = User.builder().id(UUID.randomUUID()).email("fixture@example.invalid")
                .oauthProvider("google").oauthSubject("subject-2").isActive(true).build();
        when(identity.getAttribute("email")).thenReturn("fixture@example.invalid");
        when(identity.getAttribute("sub")).thenReturn("subject-2");
        when(identity.getAttribute("email_verified")).thenReturn(true);
        when(users.findByOauthProviderAndOauthSubject("google", "subject-2"))
                .thenReturn(Optional.of(user));
        when(tickets.issue(user.getId())).thenReturn("synthetic-single-use-ticket");

        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(tokens, users, profiles, tickets);
        ReflectionTestUtils.setField(handler, "corsAllowedOrigins", "http://localhost:5173");
        ReflectionTestUtils.setField(handler, "ticketHandoff", true);
        var response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                new UsernamePasswordAuthenticationToken(identity, null));

        assertEquals(302, response.getStatus());
        assertTrue(response.getRedirectedUrl().contains("ticket=synthetic-single-use-ticket"));
        assertFalse(response.getRedirectedUrl().contains("token="));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        verifyNoInteractions(tokens);
    }
}
