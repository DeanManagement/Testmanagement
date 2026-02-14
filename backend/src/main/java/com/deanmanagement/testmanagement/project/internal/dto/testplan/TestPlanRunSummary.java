package com.deanmanagement.testmanagement.project.internal.dto.testplan;

import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;

import java.time.Instant;
import java.util.UUID;

public record TestPlanRunSummary(
        UUID id,
        String name,
        String environment,
        TestRunStatus status,
        int total,
        int passed,
        int failed,
        Instant endTime
) {
}
