package com.deanmanagement.testmanagement.project.internal.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Tunables for outbound webhook delivery (PRD-003). Defaults are air-gap safe and SSRF-cautious.
 */
@ConfigurationProperties(prefix = "app.webhooks")
public record WebhookProperties(
        /* Allow delivery to loopback / private (RFC-1918) targets. Off by default (SSRF guard). */
        boolean allowPrivateTargets,
        /* Require https target URLs. On by default; only disabled for local testing. */
        Boolean requireHttps,
        /* Total delivery attempts before a delivery is marked permanently failed. */
        Integer maxAttempts,
        /* Backoff (minutes) before each retry; index 0 = delay before attempt 2, etc. */
        List<Long> backoffMinutes,
        Integer connectTimeoutMs,
        Integer readTimeoutMs
) {
    public WebhookProperties {
        if (requireHttps == null) requireHttps = true;
        if (maxAttempts == null) maxAttempts = 3;
        if (backoffMinutes == null || backoffMinutes.isEmpty()) backoffMinutes = List.of(1L, 5L, 30L);
        if (connectTimeoutMs == null) connectTimeoutMs = 5000;
        if (readTimeoutMs == null) readTimeoutMs = 10000;
    }

    /** Backoff in minutes before the given attempt number (1-based; attempt 1 has no prior wait). */
    public long backoffMinutesForAttempt(int attempt) {
        int idx = Math.max(0, attempt - 2);
        if (idx >= backoffMinutes.size()) {
            return backoffMinutes.get(backoffMinutes.size() - 1);
        }
        return backoffMinutes.get(idx);
    }
}
