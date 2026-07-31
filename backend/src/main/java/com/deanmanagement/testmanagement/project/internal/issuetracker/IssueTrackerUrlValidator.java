package com.deanmanagement.testmanagement.project.internal.issuetracker;

import com.deanmanagement.testmanagement.shared.net.OutboundUrlValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validates the tracker base URL a project admin supplies. Self-hosted GitLab and Forgejo mean the
 * URL is user-controlled, which makes it an SSRF vector; the rules live in
 * {@link OutboundUrlValidator} and are shared with webhooks and OIDC issuers.
 */
@Component
@RequiredArgsConstructor
public class IssueTrackerUrlValidator {

    private static final String LABEL = "Issue tracker URL";

    private final IssueTrackerProperties properties;

    /** @throws IllegalArgumentException if the URL is not an acceptable tracker target. */
    public void validate(String url) {
        OutboundUrlValidator.validate(url, LABEL, properties.requireHttps(), properties.allowPrivateTargets());
    }
}
