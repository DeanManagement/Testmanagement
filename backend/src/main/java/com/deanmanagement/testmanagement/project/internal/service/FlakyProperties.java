package com.deanmanagement.testmanagement.project.internal.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for flakiness detection (PRD-016). Defaults are deliberately conservative: it is better
 * to under-report than to label a team's tests flaky on thin evidence.
 */
@ConfigurationProperties(prefix = "app.flaky")
public record FlakyProperties(
        /* How many recent terminal results to consider per test case. */
        Integer window,
        /* Score at or above which a case counts as flaky. */
        Double threshold,
        /* Below this many considered results, a case is never called flaky. */
        Integer minRuns,
        /* Whether to maintain the label automatically. Off: labels are user-owned. */
        boolean autoLabel,
        String label
) {
    public FlakyProperties {
        if (window == null || window < 2) window = 20;
        if (threshold == null || threshold < 0 || threshold > 1) threshold = 0.3;
        if (minRuns == null || minRuns < 2) minRuns = 5;
        if (label == null || label.isBlank()) label = "flaky";
    }
}
