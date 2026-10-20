package com.hkv.AiTherapy.security;

import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.repository.TherapyProfileRepository;
import com.hkv.AiTherapy.repository.UserRepository;
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
        User user = User.builder().id(UUID.randomUUID()).email("fixture@example.invalid").build();
        when(oauthUser.getAttribute("email")).thenReturn("fixture@example.invalid");
        when(oauthUser.getAttribute("name")).thenReturn("Fixture");
        when(oauthUser.getAttribute("sub")).thenReturn("synthetic-subject");
        when(users.findByOauthProviderAndOauthSubject("google", "synthetic-subject"))
                .thenReturn(Optional.of(user));
        when(tokens.generateAccessToken(user)).thenReturn(sensitiveToken);
        when(profiles.existsByUserId(user.getId())).thenReturn(true);

        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(tokens, users, profiles);
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
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens, mock(StringRedisTemplate.class));
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + sensitiveToken);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        assertFalse(output.getOut().contains(sensitiveToken));
        assertFalse(output.getErr().contains(sensitiveToken));
    }
}
