package com.deanmanagement.testmanagement.project.internal.issuetracker;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for issue-tracker integration (PRD-010). Defaults mirror the webhook conventions:
 * SSRF-cautious, https-only, and short timeouts so a slow tracker cannot stall a request thread.
 */
@ConfigurationProperties(prefix = "app.issuetracker")
public record IssueTrackerProperties(
        /* AES key for the stored API tokens, base64-encoded (16, 24 or 32 raw bytes). */
        String encryptionKey,
        /* Allow trackers on loopback / private addresses. Off by default (SSRF guard). */
        boolean allowPrivateTargets,
        /* Require https base URLs. On by default; only disabled for local testing. */
        Boolean requireHttps,
        Integer connectTimeoutMs,
        Integer readTimeoutMs,
        /* How often the status poller runs. */
        Long pollIntervalMs,
        /* Maximum links refreshed per poll, so a large backlog cannot burst the provider's API. */
        Integer pollBatchSize,
        /* Skip a link whose state was checked more recently than this. */
        Long pollMinAgeMs
) {
    public IssueTrackerProperties {
        if (requireHttps == null) requireHttps = true;
        if (connectTimeoutMs == null) connectTimeoutMs = 5000;
        if (readTimeoutMs == null) readTimeoutMs = 10000;
        if (pollIntervalMs == null) pollIntervalMs = 300_000L;
        if (pollBatchSize == null) pollBatchSize = 50;
        if (pollMinAgeMs == null) pollMinAgeMs = 240_000L;
    }
}
