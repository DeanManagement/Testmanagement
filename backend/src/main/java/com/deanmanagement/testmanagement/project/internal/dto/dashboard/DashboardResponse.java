package com.deanmanagement.testmanagement.project.internal.dto.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DashboardResponse(
        DashboardTotals totals,
        Map<String, Long> testCasesByStatus,
        Map<String, Long> testCasesByPriority,
        Map<String, Long> latestResultsByStatus,
        double overallPassRate,
        List<RecentTestRunResponse> recentTestRuns,
        List<PassRateTrendEntry> passRateTrend
) {
    public record DashboardTotals(
            long totalTestCases,
            long totalTestSuites,
            long totalTestRuns,
            long completedTestRuns
    ) {}

    public record RecentTestRunResponse(
            UUID id,
            String name,
            String environment,
            String status,
            Instant startTime,
            Instant endTime,
            int total,
            int passed,
            int failed
    ) {}

    public record PassRateTrendEntry(
            UUID testRunId,
            String name,
            Instant completedAt,
            double passRate
    ) {}
}
