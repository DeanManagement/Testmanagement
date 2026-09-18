package com.deanmanagement.testmanagement.project.internal.dto.environment;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A test case's most recent executed result in one environment (PRD-032). {@code environmentId}
 * and {@code environmentName} are null for runs without an environment ("unspecified").
 */
public record EnvironmentResultResponse(
        UUID environmentId,
        String environmentName,
        TestResultStatus status,
        UUID runId,
        String runKey,
        Instant executedAt
) {
}
