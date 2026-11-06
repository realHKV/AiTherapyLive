package com.hkv.AiTherapy.security;

import com.hkv.AiTherapy.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderCompatibilityTest {

    @Test
    void signedTokenRoundTripsAndRejectsTampering() throws Exception {
        var keys = KeyPairGenerator.getInstance("RSA");
        keys.initialize(2048);
        var pair = keys.generateKeyPair();

        var provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "privateKeyBase64",
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        ReflectionTestUtils.setField(provider, "publicKeyBase64",
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        ReflectionTestUtils.setField(provider, "accessTokenExpirySeconds", 60L);
        provider.init();

        var userId = UUID.randomUUID();
        var token = provider.generateAccessToken(User.builder()
                .id(userId).email("fixture@example.invalid").build());

        assertTrue(provider.validateToken(token));
        assertEquals(userId.toString(), provider.getUserIdFromToken(token));
        assertNotNull(provider.getJti(token));
        assertEquals(userId.toString(), provider.getAuthentication(token).getName());
        assertFalse(provider.validateToken(token.substring(0, token.length() - 1) + "!"));
    }
}
