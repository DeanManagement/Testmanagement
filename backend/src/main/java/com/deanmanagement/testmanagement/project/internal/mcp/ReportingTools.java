package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.dashboard.DashboardResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testSuite.TestSuiteReportResponse;
import com.deanmanagement.testmanagement.project.internal.service.DashboardService;
import com.deanmanagement.testmanagement.project.internal.service.FlakyTestService;
import com.deanmanagement.testmanagement.project.internal.service.TestSuiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The aggregate views a tester reads before deciding what to do next — all read-only.
 *
 * <p>There is deliberately no run-report tool: {@code get_test_run} already returns every result
 * and {@code list_test_runs} the counts, so it would only restate them.
 */
@Service
@RequiredArgsConstructor
public class ReportingTools {

    private static final int DEFAULT_FLAKY_LIMIT = 10;
    private static final int MAX_FLAKY_LIMIT = 50;

    private final McpCallerContext callerContext;
    private final DashboardService dashboardService;
    private final FlakyTestService flakyTestService;
    private final TestSuiteService testSuiteService;

    @McpTool(
            name = "get_project_dashboard",
            description = """
                    The project at a glance: how many cases, suites and runs there are, test cases
                    by status and priority, where each case's most recent result stands, the
                    overall pass rate, and the pass-rate trend over recent completed runs. Start
                    here when asked "how is the project doing".
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.Dashboard getProjectDashboard() {
        var caller = callerContext.require();
        DashboardResponse dashboard = dashboardService.getDashboard(caller.projectId());

        List<McpDtos.PassRatePoint> trend = dashboard.passRateTrend() == null ? List.of()
                : dashboard.passRateTrend().stream()
                        .map(p -> new McpDtos.PassRatePoint(p.testRunId(), p.name(),
                                p.completedAt(), p.passRate()))
                        .toList();
        return new McpDtos.Dashboard(
                dashboard.totals().totalTestCases(), dashboard.totals().totalTestSuites(),
                dashboard.totals().totalTestRuns(), dashboard.totals().completedTestRuns(),
                dashboard.testCasesByStatus(), dashboard.testCasesByPriority(),
                dashboard.latestResultsByStatus(), dashboard.overallPassRate(), trend);
    }

    @McpTool(
            name = "list_flaky_tests",
            description = """
                    Test cases whose outcome keeps flipping between PASSED and FAILED across runs,
                    flakiest first. flakyScore is the share of consecutive results that changed
                    outcome; failRate is the share that failed. A high failRate with a low
                    flakyScore is a consistently broken test, not a flaky one.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.FlakyTestList listFlakyTests(
            @McpToolParam(description = "How many to return, default 10, max 50", required = false)
            Integer limit) {

        var caller = callerContext.require();
        int capped = limit == null || limit < 1 ? DEFAULT_FLAKY_LIMIT
                : Math.min(limit, MAX_FLAKY_LIMIT);
        List<McpDtos.FlakyTest> flaky = flakyTestService.findFlaky(caller.projectId(), capped)
                .stream()
                .map(f -> new McpDtos.FlakyTest(f.testCaseId(), f.testCaseKey(), f.title(),
                        f.flakyScore(), f.failRate(), f.runsConsidered()))
                .toList();
        return new McpDtos.FlakyTestList(flaky, flaky.size());
    }

    @McpTool(
            name = "get_test_suite_report",
            description = """
                    Where a suite stands right now: for every test case in it, the most recent
                    result across all COMPLETED runs, plus the counts and pass rate. A case with
                    no status has no result in a completed run yet (counted as untested). This answers "is the smoke
                    suite green" without picking a run first.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.SuiteReport getTestSuiteReport(
            @McpToolParam(description = "Test suite UUID") UUID id) {

        var caller = callerContext.require();
        TestSuiteReportResponse report = testSuiteService.getReport(caller.projectId(), id);

        List<McpDtos.SuiteCaseResult> results = report.results() == null ? List.of()
                : report.results().stream()
                        .map(r -> new McpDtos.SuiteCaseResult(r.testCaseId(), r.testCaseTitle(),
                                r.status(), r.testRunId(), r.testRunName()))
                        .toList();
        return new McpDtos.SuiteReport(report.id(), report.name(), report.total(), report.passed(),
                report.failed(), report.blocked(), report.skipped(), report.untested(),
                report.passRate(), results);
    }
}
