package com.deanmanagement.testmanagement.project.internal.dto;

import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * List projection of a test run: result counts instead of the full {@code results} collection,
 * so list pages never materialize every result row (and its test case) per run.
 */
public record TestRunSummaryResponse(
        UUID id,
        String key,
        String name,
        String environment,
        TestRunStatus status,
        Instant startTime,
        Instant endTime,
        String executorName,
        String completedByName,
        String reopenReason,
        UUID testPlanId,
        String testPlanName,
        UUID allureReportId,
        UUID projectId,
        String projectKey,
        int total,
        int passed,
        int failed,
        int blocked,
        int skipped,
        int pending,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy
) {
}
