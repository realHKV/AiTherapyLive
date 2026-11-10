package com.hkv.AiTherapy.service.auth;

import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.dto.request.LoginRequest;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReviewerLoginTest {
    @Test
    void reviewerPasswordLoginIsMarkedReadOnly() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        JwtTokenProvider tokens = mock(JwtTokenProvider.class);
        TokenService refreshTokens = mock(TokenService.class);
        AuthService auth = new AuthService(users, passwords, tokens, refreshTokens);
        ReflectionTestUtils.setField(auth, "accessTokenExpirySeconds", 900L);

        User reviewer = User.builder().id(UUID.randomUUID()).email("review@example.invalid")
                .passwordHash("synthetic-hash").oauthProvider("razorpay-review").isActive(true).build();
        when(users.findByEmail(reviewer.getEmail())).thenReturn(Optional.of(reviewer));
        when(passwords.matches("synthetic-password", "synthetic-hash")).thenReturn(true);
        when(tokens.generateAccessToken(reviewer)).thenReturn("synthetic-access-token");
        when(refreshTokens.createRefreshToken(eq(reviewer), any())).thenReturn("synthetic-refresh-token");

        LoginRequest request = new LoginRequest();
        request.setEmail(reviewer.getEmail());
        request.setPassword("synthetic-password");
        var result = auth.login(request, "synthetic-device");

        assertTrue(result.getUser().isReadOnly());
        assertEquals(reviewer.getEmail(), result.getUser().getEmail());
    }
}
