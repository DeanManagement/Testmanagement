package com.deanmanagement.testmanagement.project.internal.dto.effort;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;

import java.time.Instant;

/**
 * The slice of a test result the plan burn-down needs (PRD-036 §3.2). Queried as a projection so
 * a plan with thousands of results never loads their steps or screenshots.
 */
public record BurnDownRow(
        Instant createdAt,
        Instant executedAt,
        TestResultStatus status,
        /** The case's live estimate; null when it has none. */
        Integer estimateMinutes
) {
    /** Executed before executedAt existed: we know it isn't pending, but not when it was done. */
    public boolean isLegacy() {
        return status != TestResultStatus.PENDING && executedAt == null;
    }
}
