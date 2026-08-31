package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.TestResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestSuiteResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.service.TestRunService;
import com.deanmanagement.testmanagement.project.internal.service.TestSuiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Execution tools (PRD-027 §3.2) — open a run, fill it in, close it.
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
@RequiredArgsConstructor
public class TestRunWriteTools {

    private final McpCallerContext callerContext;
    private final TestRunService testRunService;
    private final TestSuiteService testSuiteService;
    private final McpWriteThrottle writeThrottle;
    private final McpValidator validator;

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
            @McpToolParam(description = "Environment the run targets, e.g. staging", required = false)
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
            name = "record_test_result",
            description = """
                    Record the outcome of one test case in a run, as you finish executing it.
                    status: PASSED | FAILED | BLOCKED | SKIPPED.
                    Identify the result either by testCaseId or, when the same case appears more
                    than once in the run because it is parameterized, by the resultId that
                    get_test_run returns for it.
                    Put what you observed in comment — for a failure that is the evidence someone
                    reads later, so be specific.
                    Recording the first result on a PLANNED run starts it. When you are done, call
                    complete_test_run.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.RecordedResult recordTestResult(
            @McpToolParam(description = "Test run UUID") UUID runId,
            @McpToolParam(description = "Outcome: PASSED | FAILED | BLOCKED | SKIPPED")
            TestResultStatus status,
            @McpToolParam(description = "Test case UUID; omit if you pass resultId", required = false)
            UUID testCaseId,
            @McpToolParam(description = "Result UUID from get_test_run; use when testCaseId is "
                    + "ambiguous", required = false) UUID resultId,
            @McpToolParam(description = "What you observed", required = false) String comment,
            @McpToolParam(description = "URL of a related defect", required = false) String defectLink) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());

        if (status == null) {
            throw new McpToolException("status is required: PASSED, FAILED, BLOCKED or SKIPPED.");
        }
        if (status == TestResultStatus.PENDING) {
            throw new McpToolException("PENDING is the starting state, not an outcome. Record "
                    + "PASSED, FAILED, BLOCKED or SKIPPED, or leave the result alone.");
        }
        if (testCaseId == null && resultId == null) {
            throw new McpToolException("Pass either testCaseId or resultId.");
        }

        TestRunResponse run = testRunService.findById(caller.projectId(), runId);
        if (run.status() == TestRunStatus.COMPLETED || run.status() == TestRunStatus.ABORTED) {
            throw new McpToolException("Run " + run.key() + " is " + run.status()
                    + ", so its results are final. Create a new run to re-test.");
        }

        TestResultResponse target = resolve(run, resultId, testCaseId);

        // Started before the result is written. Both events publish AFTER_COMMIT onto an async
        // pool, so this orders the publishing, not the delivery — do not read it as a guarantee
        // that RUN_STARTED arrives first. What it does buy is the transaction: a failure below
        // rolls the start back with it, so no run is left started by a call that did not record.
        TestRunStatus runStatus = startIfPlanned(caller, run);

        boolean added = false;
        UUID recordedId;
        if (target != null) {
            // Absent comment/defectLink mean "leave alone", not "clear". UpdateTestResultRequest
            // has no absent-versus-null distinction and updateResult assigns all three fields
            // unconditionally, so passing the arguments straight through would erase the evidence
            // on any re-record — including the comment this same tool wrote a moment earlier, and
            // anything a human had already put on the pending result. Since the tool advertises
            // idempotentHint and tells the agent to retry, that is the likeliest path through it.
            var update = new UpdateTestResultRequest(status,
                    comment == null ? target.comment() : comment,
                    defectLink == null ? target.defectLink() : defectLink);
            validator.validate(update);
            recordedId = testRunService.updateResult(caller.projectId(), runId, target.id(), update)
                    .id();
        } else {
            // Only reached when the case genuinely is not in the run. Appending is right for
            // exploratory testing, but it is also what a mistyped id looks like, so the response
            // says which happened.
            var create = new CreateTestResultRequest(testCaseId, status, comment, defectLink);
            validator.validate(create);
            target = testRunService.addResult(caller.projectId(), runId, create);
            recordedId = target.id();
            added = true;
        }

        TestResultResponse recorded = target;
        return new McpDtos.RecordedResult(recordedId, recorded.testCaseId(),
                recorded.testCaseTitle(), status, added, runStatus);
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
            @McpToolParam(description = "Test run UUID") UUID runId,
            @McpToolParam(description = "COMPLETED (default) or ABORTED", required = false)
            TestRunStatus status) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());

        TestRunStatus target = status == null ? TestRunStatus.COMPLETED : status;
        if (target != TestRunStatus.COMPLETED && target != TestRunStatus.ABORTED) {
            throw new McpToolException("status must be COMPLETED or ABORTED. To start a run, "
                    + "record a result on it.");
        }

        TestRunResponse run = testRunService.findById(caller.projectId(), runId);

        // Already where the agent asked for: idempotent, because a retry after a dropped response
        // should get the counts rather than an error about something it already achieved.
        if (run.status() == target) {
            return counts(run);
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
            run = transition(caller, run, TestRunStatus.IN_PROGRESS);
        }
        return counts(transition(caller, run, target));
    }

    /**
     * Status-only update.
     *
     * <p>Name, environment and plan are passed as null rather than echoed back from the response:
     * {@code TestRunService.update} treats null as "unchanged" (PRD-027 §3.2), so an agent closing
     * a run cannot revert a rename a human made while it was still executing.
     */
    private TestRunResponse transition(McpCallerContext.Caller caller, TestRunResponse run,
                                       TestRunStatus target) {
        return testRunService.update(caller.projectId(), run.id(),
                new UpdateTestRunRequest(null, null, target, null, null), caller.userId());
    }

    /**
     * Finds the result this call is about, or null when the case is not in the run.
     *
     * <p>The ambiguity refusal is the point. A parameterized case (PRD-015) expands into one result
     * per parameter set, so a test case id can name three rows; picking one would silently record
     * an outcome against the wrong data set. The message lists the candidates so the agent can
     * retry with a resultId, which is why {@code get_test_run} publishes them.
     */
    private TestResultResponse resolve(TestRunResponse run, UUID resultId, UUID testCaseId) {
        List<TestResultResponse> results = run.results() == null ? List.of() : run.results();

        if (resultId != null) {
            TestResultResponse byId = results.stream()
                    .filter(r -> resultId.equals(r.id()))
                    .findFirst()
                    .orElseThrow(() -> new McpToolException("No result " + resultId + " in run "
                            + run.key() + ". Call get_test_run to see its results."));
            // Both supplied and disagreeing means the agent has lost track of which is which —
            // most easily by reusing a resultId from an earlier run. Recording against whichever
            // one happens to win would succeed, and would put the outcome on the wrong test case.
            if (testCaseId != null && !testCaseId.equals(byId.testCaseId())) {
                throw new McpToolException("Result " + resultId + " belongs to test case "
                        + byId.testCaseId() + ", not " + testCaseId + ". Pass only one of them, or "
                        + "call get_test_run to see which result you mean.");
            }
            return byId;
        }

        List<TestResultResponse> matches = results.stream()
                .filter(r -> testCaseId.equals(r.testCaseId()))
                .toList();
        if (matches.size() > 1) {
            String candidates = matches.stream()
                    .map(r -> r.id() + (r.parameterSetName() == null ? ""
                            : " (" + r.parameterSetName() + ")"))
                    .collect(Collectors.joining(", "));
            throw new McpToolException("Test case " + testCaseId + " has " + matches.size()
                    + " results in run " + run.key() + ", because it is parameterized. Pass one of "
                    + "these as resultId: " + candidates);
        }
        return matches.isEmpty() ? null : matches.getFirst();
    }

    /**
     * Moves a PLANNED run to IN_PROGRESS on its first recorded result, which is what stamps
     * {@code startTime} and fires {@code RUN_STARTED}.
     *
     * <p>Implicit rather than a fourth {@code start_test_run} tool on purpose: a run holding
     * results while still PLANNED is a false record, and a ceremonial call an agent must remember
     * is one it will eventually forget — silently, since nothing would fail.
     */
    private TestRunStatus startIfPlanned(McpCallerContext.Caller caller, TestRunResponse run) {
        if (run.status() != TestRunStatus.PLANNED) {
            return run.status();
        }
        return transition(caller, run, TestRunStatus.IN_PROGRESS).status();
    }

    private static McpDtos.CompletedTestRun counts(TestRunResponse run) {
        List<TestResultResponse> results = run.results() == null ? List.of() : run.results();
        return new McpDtos.CompletedTestRun(run.id(), run.key(), run.status(), results.size(),
                count(results, TestResultStatus.PASSED),
                count(results, TestResultStatus.FAILED),
                count(results, TestResultStatus.BLOCKED),
                count(results, TestResultStatus.SKIPPED),
                count(results, TestResultStatus.PENDING));
    }

    private static int count(List<TestResultResponse> results, TestResultStatus status) {
        return (int) results.stream().filter(r -> r.status() == status).count();
    }
}
