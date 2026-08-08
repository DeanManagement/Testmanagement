package com.deanmanagement.testmanagement.project.internal.buildserver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A registered build server means the base URL is admin-supplied, so these rules are the SSRF
 * boundary for the whole feature (PRD-024) — the same contract as webhooks and issue trackers.
 */
class BuildServerUrlValidatorTest {

    private static BuildServerUrlValidator validator(boolean allowPrivate, Boolean requireHttps) {
        return new BuildServerUrlValidator(new BuildServerProperties(
                allowPrivate, requireHttps, null, null, null, null, null, null));
    }

    private static final BuildServerUrlValidator STRICT = validator(false, true);

    @Test
    void acceptsAPublicHttpsUrl() {
        assertThatCode(() -> STRICT.validate("https://gitlab.com")).doesNotThrowAnyException();
    }

    @Test
    void rejectsPlainHttpByDefault() {
        // The access token would otherwise travel in the clear.
        assertThatThrownBy(() -> STRICT.validate("http://jenkins.example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must use https");
    }

    @Test
    void rejectsANonAbsoluteUrl() {
        assertThatThrownBy(() -> STRICT.validate("gitlab.com/api"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute");
    }

    @Test
    void rejectsNonHttpSchemes() {
        assertThatThrownBy(() -> validator(false, false).validate("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http(s)");
    }

    @Test
    void rejectsLoopback() {
        assertThatThrownBy(() -> validator(false, false).validate("http://127.0.0.1:8080"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private or loopback");
    }

    @Test
    void rejectsPrivateRanges() {
        assertThatThrownBy(() -> validator(false, false).validate("http://10.0.0.5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private or loopback");
        assertThatThrownBy(() -> validator(false, false).validate("http://192.168.1.10"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private or loopback");
    }

    @Test
    void rejectsLinkLocalCloudMetadataAddress() {
        assertThatThrownBy(() -> validator(false, false).validate("http://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private or loopback");
    }

    @Test
    void allowsPrivateTargetsWhenExplicitlyEnabled() {
        assertThatCode(() -> validator(true, false).validate("http://127.0.0.1:8080"))
                .doesNotThrowAnyException();
    }
}
