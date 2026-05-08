package com.hkv.AiTherapy.encryption;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption service.
 *
 * <p>Encrypted format (all stored as a single Base64 string):
 * {@code base64( IV[12 bytes] || CipherText || GCM Auth Tag[16 bytes] )}
 *
 * <p>The master key is loaded from the {@code ENCRYPTION_MASTER_KEY} environment variable
 * (a 32-byte Base64-encoded value). It is NEVER hardcoded.
 */
@Service
public class EncryptionService {

    private static final String ALGORITHM        = "AES/GCM/NoPadding";
    private static final int    GCM_IV_LENGTH    = 12;   // 96-bit IV — recommended for GCM
    private static final int    GCM_TAG_BITS     = 128;  // 128-bit authentication tag

    @Value("${encryption.master-key}")
    private String masterKeyBase64;

    private SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() {
        if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
            throw new IllegalStateException(
                "ENCRYPTION_MASTER_KEY environment variable is not set. " +
                "Generate one with: openssl rand -base64 32");
        }
        byte[] keyBytes = Base64.getDecoder().decode(masterKeyBase64.trim());
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                "ENCRYPTION_MASTER_KEY must be exactly 32 bytes (256 bits) when decoded. " +
                "Got " + keyBytes.length + " bytes.");
        }
        secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts plaintext using AES-256-GCM.
     *
     * @param plainText the sensitive string to encrypt
     * @return Base64-encoded string containing IV + ciphertext + GCM tag
     */
    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] cipherText = cipher.doFinal(plainText.getBytes());

            // Prepend IV to cipherText so we can extract it during decryption
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new EncryptionException("Failed to encrypt value", e);
        }
    }

    /**
     * Decrypts a Base64-encoded AES-256-GCM ciphertext.
     *
     * @param encryptedBase64 the value as stored in the database
     * @return the original plaintext string
     */
    public String decrypt(String encryptedBase64) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

            return new String(cipher.doFinal(cipherText));
        } catch (Exception e) {
            throw new EncryptionException("Failed to decrypt value", e);
        }
    }
}
