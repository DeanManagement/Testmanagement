package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.filter.TestSuiteListFilter;
import com.deanmanagement.testmanagement.project.internal.dto.testSuite.CreateTestSuiteRequest;
import com.deanmanagement.testmanagement.project.internal.dto.TestSuiteResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.CreateTestPlanRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.TestPlanResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.TestPlanSummaryResponse;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlanStatus;
import com.deanmanagement.testmanagement.project.internal.service.TestPlanService;
import com.deanmanagement.testmanagement.project.internal.service.TestSuiteService;
import com.deanmanagement.testmanagement.shared.PageableUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Suite and plan tools (PRD-025 §3.4) — the grouping layer above individual test cases.
 */
@Service
@RequiredArgsConstructor
public class TestPlanningTools {

    private final McpCallerContext callerContext;
    private final TestSuiteService testSuiteService;
    private final TestPlanService testPlanService;
    private final McpWriteThrottle writeThrottle;

    // --- suites ----------------------------------------------------------------------------

    @McpTool(
            name = "list_test_suites",
            description = """
                    The project's test suites — named groupings of test cases. Paged; check
                    totalElements and hasMore before concluding a suite does not exist.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.SuitePage listTestSuites(
            @McpToolParam(description = "Free-text query over the suite name", required = false) String query,
            @McpToolParam(description = "Zero-based page number, default 0", required = false) Integer page,
            @McpToolParam(description = "Page size, default 50, max 200", required = false) Integer size) {

        var caller = callerContext.require();
        Page<TestSuiteResponse> result = testSuiteService.findByProject(
                caller.projectId(), new TestSuiteListFilter(blankToNull(query)), pageable(page, size));

        List<McpDtos.SuiteSummary> summaries = result.getContent().stream()
                .map(s -> new McpDtos.SuiteSummary(s.id(), s.name(), s.description(),
                        s.testCases() == null ? 0 : s.testCases().size()))
                .toList();
        return new McpDtos.SuitePage(summaries, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.hasNext());
    }

    @McpTool(
            name = "get_test_suite",
            description = "One test suite with the test cases it contains.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.SuiteDetail getTestSuite(
            @McpToolParam(description = "Test suite UUID") UUID id) {
        var caller = callerContext.require();
        TestSuiteResponse suite = testSuiteService.findById(caller.projectId(), id);
        return new McpDtos.SuiteDetail(suite.id(), suite.name(), suite.description(),
                suite.testCases() == null ? List.of() : suite.testCases().stream()
                        .map(tc -> new McpDtos.TestCaseRef(tc.id(), tc.title()))
                        .toList());
    }

    @McpTool(
            name = "create_test_suite",
            description = """
                    Create a test suite grouping existing test cases. Pass the UUIDs returned by
                    search_test_cases or create_test_case; a suite may also start empty.
                    Every id must name a case in this project — if any does not, nothing is created
                    and you get an error naming it, rather than a quietly incomplete suite.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.CreatedSuite createTestSuite(
            @McpToolParam(description = "Suite name, max 255 characters") String name,
            @McpToolParam(description = "What this suite covers", required = false) String description,
            @McpToolParam(description = "UUIDs of test cases to include", required = false)
            Set<UUID> testCaseIds) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        if (name == null || name.isBlank()) {
            throw new McpToolException("name is required.");
        }
        TestSuiteResponse suite = testSuiteService.create(caller.projectId(),
                new CreateTestSuiteRequest(name, description, testCaseIds), caller.userId());
        return new McpDtos.CreatedSuite(suite.id(), suite.name(),
                suite.testCases() == null ? 0 : suite.testCases().size());
    }

    // --- plans -----------------------------------------------------------------------------

    @McpTool(
            name = "list_test_plans",
            description = """
                    The project's test plans. status: OPEN | IN_PROGRESS | COMPLETED | CANCELLED;
                    omit it to get them all. Not paged — projects hold few plans.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public List<McpDtos.PlanSummary> listTestPlans(
            @McpToolParam(description = "Only plans in this status", required = false) TestPlanStatus status) {
        var caller = callerContext.require();
        return testPlanService.findByProject(caller.projectId()).stream()
                .filter(p -> status == null || p.status() == status)
                .map(p -> new McpDtos.PlanSummary(p.id(), p.name(), p.description(), p.status(),
                        p.targetDate(), p.testRunCount()))
                .toList();
    }

    @McpTool(
            name = "get_test_plan",
            description = """
                    One test plan with its execution summary: how many runs it holds, how many are
                    finished, and the pass/fail breakdown across them.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.PlanDetail getTestPlan(
            @McpToolParam(description = "Test plan UUID") UUID id) {
        var caller = callerContext.require();
        TestPlanSummaryResponse s = testPlanService.getSummary(caller.projectId(), id);
        return new McpDtos.PlanDetail(s.id(), s.name(), s.status(), s.targetDate(), s.totalRuns(),
                s.completedRuns(), s.passed(), s.failed(), s.blocked(), s.skipped(), s.pending(),
                s.passRate());
    }

    @McpTool(
            name = "create_test_plan",
            description = """
                    Create a test plan — a dated container for test runs. Created unassigned on
                    purpose: assigning work to a named person is a human's decision, so a human
                    picks the assignee in the UI afterwards.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.CreatedPlan createTestPlan(
            @McpToolParam(description = "Plan name, max 255 characters") String name,
            @McpToolParam(description = "What this plan covers", required = false) String description,
            @McpToolParam(description = "Target date as YYYY-MM-DD", required = false) LocalDate targetDate) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        if (name == null || name.isBlank()) {
            throw new McpToolException("name is required.");
        }
        TestPlanResponse plan = testPlanService.create(caller.projectId(),
                new CreateTestPlanRequest(name, description, targetDate, null), caller.userId());
        return new McpDtos.CreatedPlan(plan.id(), plan.name(), plan.status());
    }

    private static Pageable pageable(Integer page, Integer size) {
        return PageableUtils.normalize(PageRequest.of(
                page == null || page < 0 ? 0 : page,
                size == null || size < 1 ? PageableUtils.DEFAULT_SIZE : size));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
