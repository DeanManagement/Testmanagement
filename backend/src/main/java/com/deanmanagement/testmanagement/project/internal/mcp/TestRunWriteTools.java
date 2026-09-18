package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestSuiteResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.project.internal.service.TestRunService;
import com.deanmanagement.testmanagement.project.internal.service.TestSuiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Execution tools (PRD-027 §3.2) — open a run and close it. Filling it in is
 * {@link TestResultRecordingTools} per case and {@link TestStepRecordingTools} per step.
 *
 * <p>These exist because {@link TestRunReadTools} left an agent able to see which tests failed and
 * unable to run any. PRD-005's batch ingestion endpoint is not a substitute: it takes a whole run
 * of results in one request, which suits CI and does not suit an agent that has one result at a
 * time and does not yet know how many there will be. That endpoint stays the right answer for the
 * batch case and is untouched.
 *
 * <p>No tool here takes a project id — {@link McpCallerContext} derives the scope from the key, as
 * everywhere else in this package.
 */
@Service
@InToolGroup(McpToolGroup.EXECUTION)
@RequiredArgsConstructor
public class TestRunWriteTools {

    private final McpCallerContext callerContext;
    private final TestRunService testRunService;
    private final TestSuiteService testSuiteService;
    private final McpWriteThrottle writeThrottle;
    private final McpValidator validator;
    private final TestRunRepository testRunRepository;
    private final McpRunSupport runSupport;

    @McpTool(
            name = "create_test_run",
            description = """
                    Open a test run and seed it with the cases you intend to execute. Each seeded
                    case gets a PENDING result you then fill in with record_test_result.
                    Seed it either by passing testCaseIds (from search_test_cases) or by passing
                    testSuiteId to take every case in a suite; passing both takes the union. A run
                    with neither is legal and starts empty, for exploratory testing.
                    testPlanId optionally files the run under a plan. The run is created PLANNED
                    and starts automatically when you record the first result.
                    You are recorded as the executor.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.CreatedTestRun createTestRun(
            @McpToolParam(description = "Run name, max 255 characters") String name,
            @McpToolParam(description = "Environment the run targets; prefer a name from list_environments", required = false)
            String environment,
            @McpToolParam(description = "UUIDs of test cases to seed the run with", required = false)
            Set<UUID> testCaseIds,
            @McpToolParam(description = "Seed from every case in this test suite", required = false)
            UUID testSuiteId,
            @McpToolParam(description = "File the run under this test plan", required = false)
            UUID testPlanId) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        if (name == null || name.isBlank()) {
            throw new McpToolException("name is required.");
        }

        Set<UUID> seed = new LinkedHashSet<>(testCaseIds == null ? Set.of() : testCaseIds);
        if (testSuiteId != null) {
            // findById is project-scoped, so a suite from another project 404s here rather than
            // quietly contributing its cases.
            TestSuiteResponse suite = testSuiteService.findById(caller.projectId(), testSuiteId);
            if (suite.testCases() != null) {
                suite.testCases().forEach(tc -> seed.add(tc.id()));
            }
        }

        var request = new CreateTestRunRequest(name, environment, seed, testPlanId,
                caller.userId());
        validator.validate(request);

        TestRunResponse run = testRunService.create(caller.projectId(), request, caller.userId());
        return new McpDtos.CreatedTestRun(run.id(), run.key(), run.name(), run.status(),
                run.results() == null ? 0 : run.results().size());
    }

    @McpTool(
            name = "complete_test_run",
            description = """
                    Close a test run and return its final counts.
                    status: COMPLETED (default) when you executed what you set out to, or ABORTED
                    when something blocked you part-way — an aborted run is a more honest record
                    than one left open forever.
                    Results still PENDING are reported back rather than refused. A completed run
                    cannot be reopened from here; run again in a new run instead.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.CompletedTestRun completeTestRun(
            @McpToolParam(description = "Test run UUID or key, e.g. PROJ-Run-7") String runIdOrKey,
            @McpToolParam(description = "COMPLETED (default) or ABORTED", required = false)
            TestRunStatus status) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());

        TestRunStatus target = status == null ? TestRunStatus.COMPLETED : status;
        if (target != TestRunStatus.COMPLETED && target != TestRunStatus.ABORTED) {
            throw new McpToolException("status must be COMPLETED or ABORTED. To start a run, "
                    + "record a result on it.");
        }

        UUID runId = McpRunReferences.resolve(testRunRepository, caller.projectId(), runIdOrKey);
        TestRunResponse run = testRunService.findById(caller.projectId(), runId);

        // Already where the agent asked for: idempotent, because a retry after a dropped response
        // should get the counts rather than an error about something it already achieved.
        if (run.status() == target) {
            return McpRunSupport.counts(run);
        }
        // Already in the *other* terminal state is a different thing entirely and must not be
        // swallowed. Returning counts here would answer "aborted?" with a cheerful COMPLETED and
        // leave the agent to notice by diffing a field against its own request.
        if (run.status() == TestRunStatus.COMPLETED || run.status() == TestRunStatus.ABORTED) {
            throw new McpToolException("Run " + run.key() + " is already " + run.status()
                    + " and cannot be changed to " + target + ". Create a new run instead.");
        }

        // A run completed straight out of PLANNED would have an endTime and no startTime, because
        // startTime is only stamped on the PLANNED -> IN_PROGRESS edge. Go through it.
        if (run.status() == TestRunStatus.PLANNED) {
            run = runSupport.transition(caller, run, TestRunStatus.IN_PROGRESS);
        }
        return McpRunSupport.counts(runSupport.transition(caller, run, target));
    }

}
