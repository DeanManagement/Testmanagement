package com.deanmanagement.testmanagement.shared.net;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Validates a user-supplied URL the server will call out to.
 *
 * <p>Three features now accept such a URL — webhook targets (PRD-003), self-hosted issue trackers
 * (PRD-010) and OIDC issuers (PRD-012) — and each is an SSRF vector: without this the server would
 * happily fetch {@code http://169.254.169.254/} (cloud instance metadata, and therefore cloud
 * credentials) or an internal service on behalf of whoever configured it.
 *
 * <p>Each caller keeps its own allow-private and require-https switches and its own error wording,
 * so operators can loosen one feature for local testing without loosening the others.
 */
public final class OutboundUrlValidator {

    private OutboundUrlValidator() {
    }

    /**
     * @param url            the URL to check
     * @param label          how the URL is named in error messages, e.g. {@code "Webhook URL"}
     * @param requireHttps   reject plain http
     * @param allowPrivate   permit loopback, private, link-local and any-local addresses
     * @throws IllegalArgumentException if the URL is not an acceptable outbound target
     */
    public static void validate(String url, String label, boolean requireHttps, boolean allowPrivate) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + label);
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException(label + " must be absolute");
        }
        if (requireHttps && !scheme.equalsIgnoreCase("https")) {
            throw new IllegalArgumentException(label + " must use https");
        }
        if (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http")) {
            throw new IllegalArgumentException(label + " must use http(s)");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException(label + " must include a host");
        }

        if (allowPrivate) {
            return;
        }

        try {
            // Every resolved address is checked, not just the first: a hostname that resolves to
            // both a public and a private address would otherwise slip through.
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                        || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
                    throw new IllegalArgumentException(
                            label + " points to a private or loopback address, which is not allowed");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(label + " host could not be resolved");
        }
    }
}
