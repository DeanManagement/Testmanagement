package com.deanmanagement.testmanagement.project.internal.webhook;

import com.deanmanagement.testmanagement.project.internal.entity.WebhookFormat;
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

    private WebhookUrlValidator httpAllowed(boolean allowPrivate) {
        return new WebhookUrlValidator(
                new WebhookProperties(allowPrivate, false, 3, List.of(1L, 5L, 30L), 5000, 10000));
    }

    @Test
    void chatFormatRequiresHttpsEvenWhenHttpIsAllowed() {
        assertThatThrownBy(() -> httpAllowed(false).validate("http://8.8.8.8/hook", WebhookFormat.SLACK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
    }

    @Test
    void chatFormatAllowsHttpToPrivateTargetsWhenEnabled() {
        assertThatCode(() -> httpAllowed(true).validate("http://10.0.0.5/hooks/abc", WebhookFormat.SLACK))
                .doesNotThrowAnyException();
    }

    @Test
    void genericFormatFollowsTheGlobalHttpsSetting() {
        assertThatCode(() -> httpAllowed(false).validate("http://8.8.8.8/hook", WebhookFormat.GENERIC))
                .doesNotThrowAnyException();
    }
}
