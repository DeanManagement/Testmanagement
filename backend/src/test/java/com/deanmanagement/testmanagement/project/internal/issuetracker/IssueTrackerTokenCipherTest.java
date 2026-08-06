package com.deanmanagement.testmanagement.project.internal.issuetracker;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The cipher protects a credential that grants write access to someone's issue tracker, so the
 * tests care as much about how it fails as about the round trip.
 */
class IssueTrackerTokenCipherTest {

    private static final String KEY = Base64.getEncoder()
            .encodeToString("test-issue-tracker-key-32-bytes!".getBytes());

    private static IssueTrackerTokenCipher cipher(String key) {
        // Exercised through the feature key, which is the fallback path; the shared key follows the
        // same code in AesGcmCipher.
        return new IssueTrackerTokenCipher(
                new IssueTrackerProperties(key, false, null, null, null, null, null, null),
                new com.deanmanagement.testmanagement.shared.crypto.AesGcmCipher(null, "app.security.encryption-key"));
    }

    @Test
    void roundTripsAToken() {
        IssueTrackerTokenCipher cipher = cipher(KEY);
        String token = "not-a-real-token-fixture";

        assertThat(cipher.decrypt(cipher.encrypt(token))).isEqualTo(token);
    }

    @Test
    void producesADifferentCiphertextEachTime() {
        IssueTrackerTokenCipher cipher = cipher(KEY);

        String first = cipher.encrypt("same-token");
        String second = cipher.encrypt("same-token");

        // GCM leaks the key stream if an IV repeats under one key, so a fresh random IV per
        // encryption is a correctness requirement, not a nicety.
        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo(cipher.decrypt(second));
    }

    @Test
    void storedFormNeverContainsThePlaintext() {
        assertThat(cipher(KEY).encrypt("not-a-real-token-fixture")).doesNotContain("not-a-real-token-fixture");
    }

    @Test
    void refusesToDecryptWithADifferentKey() {
        String encrypted = cipher(KEY).encrypt("token");
        String otherKey = Base64.getEncoder().encodeToString("another-key-of-32-bytes-length!!".getBytes());

        assertThatThrownBy(() -> cipher(otherKey).decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to decrypt");
    }

    @Test
    void detectsTamperedCiphertext() {
        IssueTrackerTokenCipher cipher = cipher(KEY);
        byte[] raw = Base64.getDecoder().decode(cipher.encrypt("token"));
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);

        // This is the value of GCM over plain AES: a flipped bit fails the auth tag.
        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsClosedWhenNoKeyIsConfigured() {
        IssueTrackerTokenCipher cipher = cipher(null);

        assertThat(cipher.isConfigured()).isFalse();
        // Storing the token in plaintext would be the dangerous fallback; it must refuse instead.
        assertThatThrownBy(() -> cipher.encrypt("token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("encryption-key");
    }

    @Test
    void rejectsAKeyOfTheWrongLength() {
        String shortKey = Base64.getEncoder().encodeToString("too-short".getBytes());

        assertThatThrownBy(() -> cipher(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("16, 24 or 32 bytes");
    }

    @Test
    void rejectsAKeyThatIsNotBase64() {
        assertThatThrownBy(() -> cipher("not base64 !!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }
}
