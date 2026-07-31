package com.deanmanagement.testmanagement.project.internal.issuetracker;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A self-hosted tracker means the base URL is attacker-influenced if a project admin is careless or
 * malicious, so these rules are the SSRF boundary for the whole feature.
 */
class IssueTrackerUrlValidatorTest {

    private static IssueTrackerUrlValidator validator(boolean allowPrivate, Boolean requireHttps) {
        return new IssueTrackerUrlValidator(
                new IssueTrackerProperties(null, allowPrivate, requireHttps, null, null, null, null, null));
    }

    private static final IssueTrackerUrlValidator STRICT = validator(false, true);

    @Test
    void acceptsAPublicHttpsUrl() {
        assertThatCode(() -> STRICT.validate("https://gitlab.com")).doesNotThrowAnyException();
    }

    @Test
    void rejectsPlainHttpByDefault() {
        // The PRIVATE-TOKEN header would otherwise travel in the clear.
        assertThatThrownBy(() -> STRICT.validate("http://gitlab.com"))
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
        // 169.254.169.254 is the instance-metadata endpoint on the major clouds; reaching it would
        // hand out cloud credentials, which makes it the canonical SSRF target.
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
