package com.deanmanagement.testmanagement.shared.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM encryption for secrets the application must store and later use verbatim — issue-tracker
 * API tokens (PRD-010), OIDC client secrets (PRD-012). Not for passwords, which are hashed.
 *
 * <p>Stored form is base64 of {@code [12-byte IV][ciphertext+tag]}. A fresh random IV per
 * encryption is required: GCM catastrophically leaks the key stream if an IV is reused under the
 * same key, so the IV is never derived from the plaintext or a counter.
 *
 * <p>Constructed with a null key when none is configured. Callers check {@link #isConfigured()} and
 * every operation throws rather than silently falling back to plaintext storage — a stored secret
 * grants access to someone else's system, so failing closed is the only safe default.
 */
public final class AesGcmCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final String keyPropertyName;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param base64Key       key material, base64-encoded, decoding to 16, 24 or 32 bytes; null or
     *                        blank leaves the cipher unconfigured
     * @param keyPropertyName property name quoted back in errors, so operators know what to set
     */
    public AesGcmCipher(String base64Key, String keyPropertyName) {
        this.keyPropertyName = keyPropertyName;
        this.key = buildKey(base64Key, keyPropertyName);
    }

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
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (Exception e) {
            // Deliberately excludes the exception message, which can echo plaintext.
            throw new IllegalStateException("Failed to encrypt secret");
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
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // A wrong key, a truncated value and a failed auth tag are indistinguishable to the
            // caller on purpose.
            throw new IllegalStateException("Failed to decrypt secret");
        }
    }

    private void requireKey() {
        if (key == null) {
            // Names the environment variable and how to make one. The property name alone sent an
            // operator looking for a config file that does not exist in a container deployment.
            throw new IllegalArgumentException("This feature requires " + keyPropertyName
                    + " to be set. Generate one with: openssl rand -base64 32");
        }
    }

    private static SecretKeySpec buildKey(String configured, String propertyName) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(propertyName + " must be base64-encoded");
        }
        if (decoded.length != 16 && decoded.length != 24 && decoded.length != 32) {
            throw new IllegalStateException(
                    propertyName + " must decode to 16, 24 or 32 bytes (got " + decoded.length + ")");
        }
        return new SecretKeySpec(decoded, "AES");
    }
}
