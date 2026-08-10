package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunSummaryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.filter.TestRunListFilter;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.service.TestRunService;
import com.deanmanagement.testmanagement.shared.PageableUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read-only access to executions (PRD-025).
 *
 * <p>Added after using the authoring tools on a real project: an agent could write forty test cases
 * and push results through the CI ingestion API, then had no way to ask which of them failed. That
 * makes the obvious follow-up workflow — "look at the last run and write cases for what broke" —
 * impossible, and it is the loop that makes the rest of the surface worth having.
 *
 * <p>Deliberately read-only. Recording results stays with PRD-005's ingestion endpoints: that path
 * already exists, already authenticates with the same key, and is the one CI uses. A second way to
 * write results would be a worse version of it.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestRunReadTools {

    private final McpCallerContext callerContext;
    private final TestRunService testRunService;

    @McpTool(
            name = "list_test_runs",
            description = """
                    Recent test runs with their pass/fail counts, newest first. Use this to find out
                    how the project is doing before deciding what to write or fix.
                    status: PLANNED | IN_PROGRESS | COMPLETED | ABORTED.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public McpDtos.TestRunPage listTestRuns(
            @McpToolParam(description = "Free-text query over the run name", required = false) String query,
            @McpToolParam(description = "Only runs in these statuses", required = false)
            List<TestRunStatus> status,
            @McpToolParam(description = "Zero-based page number, default 0", required = false) Integer page,
            @McpToolParam(description = "Page size, default 50, max 200", required = false) Integer size) {

        var caller = callerContext.require();
        Pageable pageable = PageableUtils.normalize(PageRequest.of(
                page == null || page < 0 ? 0 : page,
                size == null || size < 1 ? PageableUtils.DEFAULT_SIZE : size));

        Page<TestRunSummaryResponse> result = testRunService.findByProject(caller.projectId(),
                new TestRunListFilter(query == null || query.isBlank() ? null : query,
                        status, null, null, null),
                pageable);

        List<McpDtos.TestRunSummary> runs = result.getContent().stream()
                .map(r -> new McpDtos.TestRunSummary(r.id(), r.key(), r.name(), r.environment(),
                        r.status(), r.total(), r.passed(), r.failed(), r.blocked(), r.skipped(),
                        r.pending(), r.endTime()))
                .toList();
        return new McpDtos.TestRunPage(runs, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.hasNext());
    }

    @McpTool(
            name = "get_test_run",
            description = """
                    One test run with its per-case results, so you can see exactly which test cases
                    failed and what the executor recorded. Pass onlyStatus to narrow it — for
                    example FAILED to get just the failures of a large run.
                    Result status: PENDING | PASSED | FAILED | BLOCKED | SKIPPED.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public McpDtos.TestRunDetail getTestRun(
            @McpToolParam(description = "Test run UUID") UUID id,
            @McpToolParam(description = "Only results in these statuses", required = false)
            List<TestResultStatus> onlyStatus) {

        var caller = callerContext.require();
        TestRunResponse run = testRunService.findById(caller.projectId(), id);

        List<McpDtos.TestResult> results = (run.results() == null ? List.<
                com.deanmanagement.testmanagement.project.internal.dto.TestResultResponse>of()
                : run.results()).stream()
                .filter(r -> onlyStatus == null || onlyStatus.isEmpty() || onlyStatus.contains(r.status()))
                .map(r -> new McpDtos.TestResult(r.testCaseId(), r.testCaseTitle(), r.status(),
                        r.comment(), r.defectLink()))
                .toList();

        return new McpDtos.TestRunDetail(run.id(), run.key(), run.name(), run.environment(),
                run.status(), run.startTime(), run.endTime(), results);
    }
}
