package com.hkv.AiTherapy.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * JPA AttributeConverter that transparently encrypts and decrypts entity fields.
 *
 * <p>Usage on any entity field:
 * <pre>{@code
 *   @Convert(converter = EncryptedStringConverter.class)
 *   @Column(name = "content")
 *   private String content;
 * }</pre>
 *
 * <p>{@code autoApply = false} — must be explicitly opted in per field.
 */
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    // Constructor injection is preferred; Spring supports it for converters since Boot 3
    private final EncryptionService encryptionService;

    @Autowired
    public EncryptedStringConverter(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) return null;
        return encryptionService.encrypt(plainText);
    }

    @Override
    public String convertToEntityAttribute(String cipherText) {
        if (cipherText == null) return null;
        return encryptionService.decrypt(cipherText);
    }
}
