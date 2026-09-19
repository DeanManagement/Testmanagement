package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse;
import com.deanmanagement.testmanagement.project.internal.service.ReleaseReadinessService;
import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
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
@InToolGroup(McpToolGroup.REPORTING)
@RequiredArgsConstructor
public class ReportingTools {

    private static final int DEFAULT_FLAKY_LIMIT = 10;
    private static final int MAX_FLAKY_LIMIT = 50;

    private final McpCallerContext callerContext;
    private final ReleaseReadinessService readinessService;
    private final DashboardService dashboardService;
    private final FlakyTestService flakyTestService;
    private final TestSuiteService testSuiteService;

    @McpTool(
            name = "get_project_dashboard",
            description = """
                    The project at a glance: how many cases, suites and runs there are, and test
                    cases by status and priority.
                    overallPassRate is the project's current health: every test case counted once,
                    by its most recent executed result in a completed run. latestResultsByStatus is
                    narrower — the results of the single most recently completed run — and
                    passRateTrend gives the pass rate of each recent completed run. A small re-test
                    changes latestResultsByStatus completely and overallPassRate only as far as the
                    re-tested cases changed. Runs still in progress count towards none of them.
                    Start here when asked "how is the project doing".
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
            name = "get_release_readiness",
            description = """
                    Is this release ready? A test plan's verdict against its release gate: GO,
                    NO_GO or NO_CRITERIA (nobody set a gate on the plan). Each configured criterion
                    lists its actual value, threshold and outcome (PASS | FAIL | NOT_APPLICABLE), so
                    a NO_GO says why. Criteria: PASS_RATE (percent, using each test case's latest
                    result in the plan, pending counting as not passed), BLOCKER_BUGS (open or
                    in-progress CRITICAL bugs in the project), COVERAGE (requirement coverage
                    percent; NOT_APPLICABLE without requirements), FLAKY_TESTS (flaky cases the plan
                    executes). A value equal to its threshold passes. Find plan ids with
                    list_test_plans.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public ReadinessResponse getReleaseReadiness(@McpToolParam(description = "Test plan UUID") UUID planId) {
        var caller = callerContext.require();
        return readinessService.readiness(caller.projectId(), planId);
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
