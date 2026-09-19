package com.deanmanagement.testmanagement.project.internal.dto.testCase;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;

import java.time.Instant;
import java.util.UUID;

/** One place a test case ran or is scheduled to run (PRD-050); pending results included. */
public record TestCaseExecutionResponse(
        UUID resultId,
        UUID runId,
        String runKey,
        String runName,
        TestRunStatus runStatus,
        String environment,
        String parameterSetName,
        TestResultStatus status,
        Instant executedAt,
        /* Who executed it (PRD-048); null while pending, or for an anonymous upload. */
        String executorName,
        Integer executedVersion,
        Long durationMs
) {
}
