package com.deanmanagement.testmanagement.project.internal.buildserver;

import com.deanmanagement.testmanagement.shared.net.OutboundUrlValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validates the build-server base URL an instance admin supplies. Self-hosted servers mean the
 * URL is user-controlled, which makes it an SSRF vector; the rules live in
 * {@link OutboundUrlValidator} and are shared with webhooks, issue trackers and OIDC issuers.
 */
@Component
@RequiredArgsConstructor
public class BuildServerUrlValidator {

    private static final String LABEL = "Build server URL";

    private final BuildServerProperties properties;

    /** @throws IllegalArgumentException if the URL is not an acceptable build-server target. */
    public void validate(String url) {
        OutboundUrlValidator.validate(url, LABEL, properties.requireHttps(), properties.allowPrivateTargets());
    }
}
