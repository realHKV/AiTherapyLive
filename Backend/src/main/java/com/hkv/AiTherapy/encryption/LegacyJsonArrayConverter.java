package com.hkv.AiTherapy.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

/** Reads V2 plaintext JSON arrays, but always encrypts newly written values. */
@Converter(autoApply = false)
public class LegacyJsonArrayConverter implements AttributeConverter<String, String> {
    private final EncryptionService encryption;
    private final ObjectMapper mapper;

    @Autowired
    public LegacyJsonArrayConverter(EncryptionService encryption, ObjectMapper mapper) {
        this.encryption = encryption;
        this.mapper = mapper;
    }

    @Override
    public String convertToDatabaseColumn(String value) {
        return value == null ? null : encryption.encrypt(value);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null) return null;
        if (stored.startsWith("[")) {
            if (!mapper.readTree(stored).isArray()) {
                throw new EncryptionException("Invalid legacy array", null);
            }
            return stored;
        }
        return encryption.decrypt(stored);
    }
}
