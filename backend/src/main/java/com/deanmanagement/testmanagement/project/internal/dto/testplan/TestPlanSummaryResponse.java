package com.deanmanagement.testmanagement.project.internal.dto.testplan;

import com.deanmanagement.testmanagement.project.internal.entity.TestPlanStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TestPlanSummaryResponse(
        UUID id,
        String name,
        TestPlanStatus status,
        LocalDate targetDate,
        int totalRuns,
        int completedRuns,
        int totalResults,
        int passed,
        int failed,
        int blocked,
        int skipped,
        int pending,
        double passRate,
        List<TestPlanRunSummary> runs
) {
}
