package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.TestResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.project.internal.service.TestRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filling in a run (PRD-027 §3.2): the outcome of a whole test case, one at a time or in a batch.
 * {@link TestRunWriteTools} opens and closes the run; {@link TestStepRecordingTools} records below
 * the case, per step.
 */
@Service
@InToolGroup(McpToolGroup.EXECUTION)
@RequiredArgsConstructor
public class TestResultRecordingTools {

    private final McpCallerContext callerContext;
    private final TestRunService testRunService;
    private final TestRunRepository testRunRepository;
    private final McpRunSupport runSupport;
    private final McpWriteThrottle writeThrottle;
    private final McpValidator validator;
    private final McpProperties properties;

    @McpTool(
            name = "record_test_result",
            description = """
                    Record the outcome of one test case in a run, as you finish executing it.
                    status: PASSED | FAILED | BLOCKED | SKIPPED.
                    The run is named by its UUID or its key (PROJ-Run-7).
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
            @McpToolParam(description = "Test run UUID or key, e.g. PROJ-Run-7") String runIdOrKey,
            @McpToolParam(description = "Outcome: PASSED | FAILED | BLOCKED | SKIPPED")
            TestResultStatus status,
            @McpToolParam(description = "Test case UUID; omit if you pass resultId", required = false)
            UUID testCaseId,
            @McpToolParam(description = "Result UUID from get_test_run; use when testCaseId is "
                    + "ambiguous", required = false) UUID resultId,
            @McpToolParam(description = "What you observed", required = false) String comment,
            @McpToolParam(description = "URL of a related defect", required = false) String defectLink,
            @McpToolParam(description = "How long the execution took, in milliseconds", required = false)
            Long durationMs) {

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

        UUID runId = McpRunReferences.resolve(testRunRepository, caller.projectId(), runIdOrKey);
        TestRunResponse run = testRunService.findById(caller.projectId(), runId);
        McpRunSupport.requireOpen(run);

        TestResultResponse target = McpRunSupport.resolveResult(run, resultId, testCaseId);

        // Started before the result is written. Both events publish AFTER_COMMIT onto an async
        // pool, so this orders the publishing, not the delivery — do not read it as a guarantee
        // that RUN_STARTED arrives first. What it does buy is the transaction: a failure below
        // rolls the start back with it, so no run is left started by a call that did not record.
        TestRunStatus runStatus = runSupport.startIfPlanned(caller, run);

        boolean added = false;
        UUID recordedId;
        if (target != null) {
            // Absent comment/defectLink leave the result's own alone (updateResult treats null as
            // unchanged), so a retried record never erases evidence already on the result.
            var update = new UpdateTestResultRequest(status, comment, defectLink, durationMs);
            validator.validate(update);
            recordedId = testRunService.updateResult(caller.projectId(), runId, target.id(), update)
                    .id();
        } else {
            // Only reached when the case genuinely is not in the run. Appending is right for
            // exploratory testing, but it is also what a mistyped id looks like, so the response
            // says which happened.
            var create = new CreateTestResultRequest(testCaseId, status, comment, defectLink, durationMs);
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
            name = "record_test_results",
            description = """
                    Record many outcomes in one call — use this whenever you have more than a
                    couple, rather than calling record_test_result in a loop.
                    Each entry needs a status (PASSED | FAILED | BLOCKED | SKIPPED) and either a
                    testCaseId or a resultId, plus an optional comment and defectLink.
                    Every entry is checked before anything is written, so a bad id fails the whole
                    call naming the offending position and leaves the run untouched — you fix that
                    entry and send the batch again. Re-recording is safe: an entry that already has
                    the status you are sending is simply set again, and omitting a comment keeps
                    the one already there.
                    Like the single-result tool, the first entry starts a PLANNED run.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.RecordedResults recordTestResults(
            @McpToolParam(description = "Test run UUID or key, e.g. PROJ-Run-7") String runIdOrKey,
            @McpToolParam(description = "The outcomes to record") List<McpDtos.ResultEntry> results) {

        var caller = callerContext.requireWriter();
        if (results == null || results.isEmpty()) {
            throw new McpToolException("results is required and must hold at least one entry.");
        }
        int max = properties.getMaxBulkSize();
        if (results.size() > max) {
            throw new McpToolException("Too many results in one call: " + results.size()
                    + ", the limit is " + max + ". Send them in batches of that size.");
        }
        // One write per result, charged up front — the budget exists to bound a runaway agent, and
        // a batch that slipped through as a single write would be the way around it.
        writeThrottle.recordWrites(caller.apiKeyId(), results.size());

        UUID runId = McpRunReferences.resolve(testRunRepository, caller.projectId(), runIdOrKey);
        TestRunResponse run = testRunService.findById(caller.projectId(), runId);
        McpRunSupport.requireOpen(run);

        /*
         * Resolved and validated in full before a single write, which is why this is one
         * transaction rather than the per-item REQUIRES_NEW that create_test_cases_bulk needs.
         *
         * That tool commits per item because a failed retry would duplicate the cases that already
         * landed. Recording is idempotent — the upsert fills the same row again and an omitted
         * comment is preserved — so resending a corrected batch is safe and cheap, and the agent
         * is better served by "nothing happened, entry 12 names a case that is not in this run"
         * than by a half-recorded run it now has to reconcile.
         */
        List<TestResultResponse> targets = new ArrayList<>(results.size());
        for (int index = 0; index < results.size(); index++) {
            McpDtos.ResultEntry entry = results.get(index);
            try {
                targets.add(validateEntry(run, entry));
            } catch (McpToolException problem) {
                throw new McpToolException("results[" + index + "]: " + problem.getMessage()
                        + " Nothing was recorded.");
            }
        }

        TestRunStatus runStatus = runSupport.startIfPlanned(caller, run);
        for (int index = 0; index < results.size(); index++) {
            McpDtos.ResultEntry entry = results.get(index);
            TestResultResponse target = targets.get(index);
            var update = new UpdateTestResultRequest(entry.status(), entry.comment(), entry.defectLink(),
                    entry.durationMs());
            validator.validate(update);
            testRunService.updateResult(caller.projectId(), runId, target.id(), update);
        }

        McpDtos.CompletedTestRun counts =
                McpRunSupport.counts(testRunService.findById(caller.projectId(), runId));
        return new McpDtos.RecordedResults(results.size(), runStatus, counts.total(),
                counts.passed(), counts.failed(), counts.blocked(), counts.skipped(),
                counts.pending());
    }

    /**
     * Checks one entry and returns the result it names.
     *
     * <p>Only seeded results can be filled in by the bulk tool: appending an ad-hoc result, which
     * {@code record_test_result} allows, is refused here. A batch is where a stale or mistyped id
     * is least likely to be noticed, and quietly growing the run by an entry the agent did not mean
     * to add is the wrong way to fail.
     */
    private TestResultResponse validateEntry(TestRunResponse run, McpDtos.ResultEntry entry) {
        if (entry == null || entry.status() == null) {
            throw new McpToolException("status is required: PASSED, FAILED, BLOCKED or SKIPPED.");
        }
        if (entry.status() == TestResultStatus.PENDING) {
            throw new McpToolException("PENDING is the starting state, not an outcome.");
        }
        if (entry.testCaseId() == null && entry.resultId() == null) {
            throw new McpToolException("pass either testCaseId or resultId.");
        }
        TestResultResponse target = McpRunSupport.resolveResult(run, entry.resultId(), entry.testCaseId());
        if (target == null) {
            throw new McpToolException("test case " + entry.testCaseId() + " has no result in run "
                    + run.key() + "; record_test_results only fills in results the run already "
                    + "holds.");
        }
        return target;
    }
}
