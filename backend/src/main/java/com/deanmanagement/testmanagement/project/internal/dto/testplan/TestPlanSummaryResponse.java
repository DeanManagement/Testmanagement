package com.deanmanagement.testmanagement.project.internal.dto.testplan;

import java.time.Instant;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
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
        List<TestPlanRunSummary> runs,
        /* PRD-034: exploration done for this plan; kept apart from run counts and passRate. */
        SessionsSummary sessions
) {

    public record SessionsSummary(int total, int completed, long totalMinutes, List<SessionItem> items) {
    }

    /** {@code minutes} is time spent so far: ended - started, or now - started while running. */
    public record SessionItem(UUID id, String key, String charter, TestRunStatus status, String testerName,
                              Instant startedAt, Instant endedAt, long minutes) {
    }
}
