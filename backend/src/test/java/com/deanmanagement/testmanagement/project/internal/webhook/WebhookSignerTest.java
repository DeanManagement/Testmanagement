package com.deanmanagement.testmanagement.project.internal.webhook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignerTest {

    private final WebhookSigner signer = new WebhookSigner();

    @Test
    void matchesKnownHmacVector() {
        // RFC-style known vector for HMAC-SHA256.
        String sig = signer.sign("key", "The quick brown fox jumps over the lazy dog");
        assertThat(sig).isEqualTo("sha256=f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    void isDeterministicAndSecretDependent() {
        assertThat(signer.sign("s1", "body")).isEqualTo(signer.sign("s1", "body"));
        assertThat(signer.sign("s1", "body")).isNotEqualTo(signer.sign("s2", "body"));
    }
}
