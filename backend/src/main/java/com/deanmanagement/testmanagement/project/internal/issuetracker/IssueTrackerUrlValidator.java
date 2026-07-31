package com.deanmanagement.testmanagement.project.internal.issuetracker;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Validates the tracker base URL a project admin supplies. Self-hosted GitLab means the URL is
 * user-controlled, which makes it an SSRF vector: without this the server would happily fetch
 * {@code http://169.254.169.254/} or an internal service on behalf of whoever configured it.
 *
 * <p>Mirrors {@code WebhookUrlValidator}; kept separate so the two features' allow-private and
 * require-https switches can be operated independently.
 */
@Component
@RequiredArgsConstructor
public class IssueTrackerUrlValidator {

    private final IssueTrackerProperties properties;

    /** @throws IllegalArgumentException if the URL is not an acceptable tracker target. */
    public void validate(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid issue tracker URL");
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("Issue tracker URL must be absolute");
        }
        if (properties.requireHttps() && !scheme.equalsIgnoreCase("https")) {
            throw new IllegalArgumentException("Issue tracker URL must use https");
        }
        if (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http")) {
            throw new IllegalArgumentException("Issue tracker URL must use http(s)");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Issue tracker URL must include a host");
        }

        if (properties.allowPrivateTargets()) {
            return;
        }

        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                        || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
                    throw new IllegalArgumentException(
                            "Issue tracker URL points to a private or loopback address, which is not allowed");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Issue tracker URL host could not be resolved");
        }
    }
}
