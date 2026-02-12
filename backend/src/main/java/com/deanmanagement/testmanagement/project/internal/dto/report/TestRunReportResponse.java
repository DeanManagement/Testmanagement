package com.deanmanagement.testmanagement.project.internal.dto.report;

import com.deanmanagement.testmanagement.project.internal.dto.TestResultResponse;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TestRunReportResponse(
        UUID id,
        String name,
        String environment,
        TestRunStatus status,
        Instant startTime,
        Instant endTime,
        int total,
        int passed,
        int failed,
        int blocked,
        int skipped,
        int pending,
        double passRate,
        List<TestResultResponse> results
) {
}
