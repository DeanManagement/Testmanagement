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
        /* PRD-049: passed of the cases with an executed latest result; null when there are none. */
        Double passRate,
        List<TestCaseLatestResult> results,
        /* PRD-049: cases with an executed latest result, of all cases in the suite. */
        Double progress
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
