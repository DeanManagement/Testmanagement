package com.deanmanagement.testmanagement.project.internal.webhook;

import com.deanmanagement.testmanagement.shared.net.OutboundUrlValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validates user-supplied webhook URLs: HTTPS only, and (unless explicitly allowed) rejects
 * loopback / private / link-local targets to limit SSRF exposure. The rules live in
 * {@link OutboundUrlValidator} and are shared with issue trackers and OIDC issuers.
 */
@Component
@RequiredArgsConstructor
public class WebhookUrlValidator {

    private static final String LABEL = "Webhook URL";

    private final WebhookProperties properties;

    /** @throws IllegalArgumentException if the URL is not an acceptable webhook target. */
    public void validate(String url) {
        OutboundUrlValidator.validate(url, LABEL, properties.requireHttps(), properties.allowPrivateTargets());
    }
}
