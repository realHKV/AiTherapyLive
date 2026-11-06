package com.hkv.AiTherapy.encryption;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class LegacyJsonArrayConverterTest {
    @Test
    void readsLegacyArrayButEncryptsEveryNewWrite() {
        var encryption = new EncryptionService();
        ReflectionTestUtils.setField(encryption, "masterKeyBase64",
                Base64.getEncoder().encodeToString(new byte[32]));
        encryption.init();
        var converter = new LegacyJsonArrayConverter(encryption, new ObjectMapper());

        String legacy = "[\"music\",\"walking\"]";
        assertEquals(legacy, converter.convertToEntityAttribute(legacy));
        String stored = converter.convertToDatabaseColumn(legacy);
        assertNotEquals(legacy, stored);
        assertEquals(legacy, converter.convertToEntityAttribute(stored));
        assertThrows(RuntimeException.class, () -> converter.convertToEntityAttribute("not-json-or-ciphertext"));
    }
}
