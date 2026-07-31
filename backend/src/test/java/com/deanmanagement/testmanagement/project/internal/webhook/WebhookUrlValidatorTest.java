package com.deanmanagement.testmanagement.project.internal.webhook;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookUrlValidatorTest {

    private WebhookUrlValidator validator(boolean allowPrivate) {
        return new WebhookUrlValidator(
                new WebhookProperties(allowPrivate, true, 3, List.of(1L, 5L, 30L), 5000, 10000));
    }

    @Test
    void rejectsNonHttps() {
        assertThatThrownBy(() -> validator(true).validate("http://example.com/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
    }

    @Test
    void rejectsLoopbackWhenPrivateNotAllowed() {
        assertThatThrownBy(() -> validator(false).validate("https://127.0.0.1/hook"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsLoopbackWhenPrivateAllowed() {
        assertThatCode(() -> validator(true).validate("https://127.0.0.1/hook"))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsPublicHttps() {
        // Use a public IP literal to avoid depending on DNS in the test environment.
        assertThatCode(() -> validator(false).validate("https://8.8.8.8/hook"))
                .doesNotThrowAnyException();
    }
}
