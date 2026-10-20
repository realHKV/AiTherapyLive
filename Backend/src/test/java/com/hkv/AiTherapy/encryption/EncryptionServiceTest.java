package com.hkv.AiTherapy.encryption;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private static final String TEST_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptedUnicodeRoundTripsAndUsesFreshNonce() {
        EncryptionService service = new EncryptionService();
        ReflectionTestUtils.setField(service, "masterKeyBase64", TEST_KEY);
        service.init();

        String plaintext = "Synthetic fixture: नमस्ते 🌱";
        String first = service.encrypt(plaintext);
        String second = service.encrypt(plaintext);
        assertNotEquals(first, second);
        assertEquals(plaintext, service.decrypt(first));
        assertEquals(plaintext, service.decrypt(second));
    }

    @Test
    void alteredCiphertextCannotBeRead() {
        EncryptionService service = new EncryptionService();
        ReflectionTestUtils.setField(service, "masterKeyBase64", TEST_KEY);
        service.init();

        byte[] encrypted = Base64.getDecoder().decode(service.encrypt("synthetic private content"));
        encrypted[encrypted.length - 1] ^= 1;
        assertThrows(EncryptionException.class,
                () -> service.decrypt(Base64.getEncoder().encodeToString(encrypted)));
    }

    @Test
    void invalidKeyFailsAtStartup() {
        EncryptionService service = new EncryptionService();
        ReflectionTestUtils.setField(service, "masterKeyBase64", "not-base64!");
        assertThrows(IllegalStateException.class, service::init);
    }
}
