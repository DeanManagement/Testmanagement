package com.deanmanagement.testmanagement.project.internal.dto.testSuite;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TestSuiteReportResponse(
        UUID id,
        String name,
        String description,
        int total,
        int passed,
        int failed,
        int blocked,
        int skipped,
        int untested,
        double passRate,
        List<TestCaseLatestResult> results
) {
    public record TestCaseLatestResult(
            UUID testCaseId,
            String testCaseTitle,
            TestResultStatus status,
            UUID testRunId,
            String testRunName,
            Instant updatedAt
    ) {
    }
}
