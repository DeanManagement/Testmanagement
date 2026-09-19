package com.deanmanagement.testmanagement.project.internal.dto.report;

import com.deanmanagement.testmanagement.project.internal.dto.TestResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.effort.EffortSummary;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;
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
        /* PRD-049: passed of executed; null when nothing was executed. */
        Double passRate,
        List<TestResultResponse> results,
        /* PRD-033: results executed against wording that was never approved; empty without review. */
        Set<UUID> unapprovedResultIds,
        /* PRD-036 */
        EffortSummary effort,
        /* PRD-048: the plan the run belongs to, if any. */
        UUID testPlanId,
        String testPlanName,
        /* PRD-049: executed of total, in percent. */
        Double progress
) {
}
