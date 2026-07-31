package com.deanmanagement.testmanagement.project.internal.issuetracker;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM encryption for stored issue-tracker API tokens (PRD-010 §3.4).
 *
 * <p>Stored form is base64 of {@code [12-byte IV][ciphertext+tag]}. A fresh random IV per
 * encryption is required — GCM catastrophically leaks the key stream if an IV is reused under the
 * same key, so the IV is never derived from the plaintext or a counter.
 *
 * <p>The key comes from {@code app.issuetracker.encryption-key}. If it is absent the cipher refuses
 * to encrypt rather than falling back to plaintext storage: a tracker token grants write access to
 * the customer's issue tracker, so failing closed is the only safe default.
 */
@Component
public class IssueTrackerTokenCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public IssueTrackerTokenCipher(IssueTrackerProperties properties) {
        this.key = buildKey(properties.encryptionKey());
    }

    /** Whether a usable key is configured. Endpoints check this to fail with a clear message. */
    public boolean isConfigured() {
        return key != null;
    }

    public String encrypt(String plaintext) {
        requireKey();
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (Exception e) {
            // Deliberately does not include the exception message, which can echo plaintext.
            throw new IllegalStateException("Failed to encrypt issue tracker token");
        }
    }

    public String decrypt(String stored) {
        requireKey();
        try {
            byte[] raw = Base64.getDecoder().decode(stored);
            ByteBuffer buffer = ByteBuffer.wrap(raw);

            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Wrong key, truncated value or a failed auth tag all land here and are indistinguishable
            // to the caller on purpose.
            throw new IllegalStateException("Failed to decrypt issue tracker token");
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalArgumentException(
                    "Issue tracker integration requires app.issuetracker.encryption-key to be set");
        }
    }

    private static SecretKeySpec buildKey(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.issuetracker.encryption-key must be base64-encoded");
        }
        if (decoded.length != 16 && decoded.length != 24 && decoded.length != 32) {
            throw new IllegalStateException(
                    "app.issuetracker.encryption-key must decode to 16, 24 or 32 bytes (got " + decoded.length + ")");
        }
        return new SecretKeySpec(decoded, "AES");
    }
}
