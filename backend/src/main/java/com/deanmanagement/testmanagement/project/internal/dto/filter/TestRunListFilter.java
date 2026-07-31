package com.deanmanagement.testmanagement.project.internal.dto.filter;

import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Filter criteria for the test-run list endpoint. All fields are optional.
 */
public record TestRunListFilter(
        String q,
        List<TestRunStatus> status,
        UUID testPlanId,
        UUID executorId,
        Instant startedAfter
) {
}
