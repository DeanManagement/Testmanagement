package com.deanmanagement.testmanagement.project.internal.webhook;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Validates user-supplied webhook URLs: HTTPS only, and (unless explicitly allowed) rejects
 * loopback / private / link-local targets to limit SSRF exposure.
 */
@Component
@RequiredArgsConstructor
public class WebhookUrlValidator {

    private final WebhookProperties properties;

    /** @throws IllegalArgumentException if the URL is not an acceptable webhook target. */
    public void validate(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid webhook URL");
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("Webhook URL must be absolute");
        }
        if (properties.requireHttps() && !scheme.equalsIgnoreCase("https")) {
            throw new IllegalArgumentException("Webhook URL must use https");
        }
        if (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http")) {
            throw new IllegalArgumentException("Webhook URL must use http(s)");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Webhook URL must include a host");
        }

        if (properties.allowPrivateTargets()) {
            return;
        }

        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                        || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
                    throw new IllegalArgumentException(
                            "Webhook URL points to a private or loopback address, which is not allowed");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Webhook URL host could not be resolved");
        }
    }
}
