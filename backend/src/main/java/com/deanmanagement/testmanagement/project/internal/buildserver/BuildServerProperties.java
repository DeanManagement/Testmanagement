package com.deanmanagement.testmanagement.project.internal.buildserver;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for build-server integration (PRD-024). Defaults mirror the issue-tracker conventions:
 * SSRF-cautious, https-only, short timeouts. The poll interval is deliberately much shorter than
 * the issue tracker's — only runs that are actively executing are polled, so a tester watching a
 * pipeline sees progress within seconds while an idle instance makes no calls at all.
 */
@ConfigurationProperties(prefix = "app.buildserver")
public record BuildServerProperties(
        /* Allow build servers on loopback / private addresses. Off by default (SSRF guard). */
        boolean allowPrivateTargets,
        /* Require https base URLs. On by default; only disabled for local testing. */
        Boolean requireHttps,
        Integer connectTimeoutMs,
        Integer readTimeoutMs,
        /* How often the status poller advances non-terminal runs. */
        Long pollIntervalMs,
        /* Maximum runs polled per pass, so a backlog cannot burst a provider's API. */
        Integer pollBatchSize,
        /* A run still non-terminal after this long is marked TIMED_OUT and no longer polled. */
        Integer runTimeoutMinutes,
        /*
         * Public URL of this instance, injected into triggered pipelines as TM_BASE_URL so a
         * workflow can address the report-back endpoints without hardcoding it. Optional; when
         * unset the variable is simply omitted.
         */
        String publicBaseUrl
) {
    public BuildServerProperties {
        if (requireHttps == null) requireHttps = true;
        if (connectTimeoutMs == null) connectTimeoutMs = 5000;
        if (readTimeoutMs == null) readTimeoutMs = 10000;
        if (pollIntervalMs == null) pollIntervalMs = 15_000L;
        if (pollBatchSize == null) pollBatchSize = 20;
        if (runTimeoutMinutes == null) runTimeoutMinutes = 120;
    }
}
