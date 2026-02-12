package com.deanmanagement.testmanagement.project.internal.dto;

import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TestRunResponse(
        UUID id,
        String name,
        String environment,
        TestRunStatus status,
        Instant startTime,
        Instant endTime,
        String executorName,
        String completedByName,
        String reopenReason,
        List<TestResultResponse> results,
        Instant createdAt,
        Instant updatedAt
) {
}
