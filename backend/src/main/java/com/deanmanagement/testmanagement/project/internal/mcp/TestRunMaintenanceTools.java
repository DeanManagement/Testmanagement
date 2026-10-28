package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CloneTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.project.internal.service.TestRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Housekeeping on a run that is not recording a result: rename it, re-file it, run it again,
 * reopen or delete it.
 *
 * <p>Separate from {@link TestRunWriteTools} and the recording tools, which own the execute-a-run
 * loop. Closing is {@code complete_test_run}'s job. Reopening was kept a human decision by
 * PRD-027 §2; the product owner has since opened it to agents (TM26), on the same terms as the UI:
 * a reason is required and lands in the audit log.
 */
@Service
@InToolGroup(McpToolGroup.EXECUTION)
@RequiredArgsConstructor
public class TestRunMaintenanceTools {

    private final McpCallerContext callerContext;
    private final TestRunService testRunService;
    private final TestRunRepository testRunRepository;
    private final McpWriteThrottle writeThrottle;
    private final McpValidator validator;
    private final McpEnvironmentGuard environmentGuard;

    @McpTool(
            name = "update_test_run",
            description = """
                    Rename a test run, change its environment, or file it under a test plan. Only
                    the fields you pass are changed. To CLEAR the environment pass an empty
                    string "".
                    This cannot change the run's status: use complete_test_run to close a run and
                    reopen_test_run to reopen one.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.UpdatedTestRun updateTestRun(
            @McpToolParam(description = "Test run UUID or key, e.g. PROJ-Run-7") String runIdOrKey,
            @McpToolParam(description = "New name, max 255 characters", required = false) String name,
            @McpToolParam(description = "New environment, a name from list_environments; \"\" clears it",
                    required = false) String environment,
            @McpToolParam(description = "File the run under this test plan", required = false)
            UUID testPlanId) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());

        if (name == null && environment == null && testPlanId == null) {
            throw new McpToolException("Nothing to update: pass name, environment or testPlanId.");
        }
        if (name != null && name.isBlank()) {
            throw new McpToolException("name must not be blank. Omit it to keep the current name.");
        }

        environmentGuard.requireKnown(caller.projectId(), environment);
        var request = new UpdateTestRunRequest(name, environment, null, null, testPlanId);
        validator.validate(request);

        UUID runId = McpRunReferences.resolve(testRunRepository, caller.projectId(), runIdOrKey);
        TestRunResponse run = testRunService.update(caller.projectId(), runId, request,
                caller.userId());
        return new McpDtos.UpdatedTestRun(run.id(), run.key(), run.name(), run.environment(),
                run.status(), run.testPlanId());
    }

    @McpTool(
            name = "clone_test_run",
            description = """
                    Open a new PLANNED run holding the same test cases as an existing run, every
                    result PENDING — the usual way to re-test after a fix, leaving the original run
                    as it was signed off. Results, comments and the test plan are not copied.
                    You are recorded as the executor of the new run.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.CreatedTestRun cloneTestRun(
            @McpToolParam(description = "UUID or key of the run to copy, e.g. PROJ-Run-7")
            String runIdOrKey,
            @McpToolParam(description = "Name of the new run, max 255 characters") String name,
            @McpToolParam(description = "Environment of the new run; omit to keep the source run's, \"\" for none", required = false)
            String environment) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());

        environmentGuard.requireKnown(caller.projectId(), environment);
        var request = new CloneTestRunRequest(name, environment);
        validator.validate(request);

        UUID sourceId = McpRunReferences.resolve(testRunRepository, caller.projectId(), runIdOrKey);
        TestRunResponse clone = testRunService.cloneRun(caller.projectId(), sourceId, request,
                caller.userId());
        // cloneRun leaves the executor empty; create_test_run attributes the run to the agent, and
        // a clone is no less the agent's run.
        testRunService.setExecutor(caller.projectId(), clone.id(), caller.userId());
        return new McpDtos.CreatedTestRun(clone.id(), clone.key(), clone.name(), clone.status(),
                clone.results() == null ? 0 : clone.results().size());
    }

    @McpTool(
            name = "reopen_test_run",
            description = """
                    Reopen a COMPLETED or ABORTED run, moving it back to IN_PROGRESS so more results
                    can be recorded; complete it again with complete_test_run when done. reason is
                    required and is written to the run and the audit log.
                    Prefer clone_test_run to re-test after a fix: reopening changes a run someone
                    may already have signed off.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.UpdatedTestRun reopenTestRun(
            @McpToolParam(description = "Test run UUID or key, e.g. PROJ-Run-7") String runIdOrKey,
            @McpToolParam(description = "Why the run is being reopened") String reason) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        if (reason == null || reason.isBlank()) {
            throw new McpToolException("reason is required: say why the run is being reopened.");
        }

        UUID runId = McpRunReferences.resolve(testRunRepository, caller.projectId(), runIdOrKey);
        TestRunStatus status = testRunService.findById(caller.projectId(), runId).status();
        if (status != TestRunStatus.COMPLETED && status != TestRunStatus.ABORTED) {
            throw new McpToolException("The run is " + status + ", not closed, so there is nothing to "
                    + "reopen: record results on it directly.");
        }
        TestRunResponse run = testRunService.update(caller.projectId(), runId,
                new UpdateTestRunRequest(null, null, TestRunStatus.IN_PROGRESS, reason.strip(), null),
                caller.userId());
        return new McpDtos.UpdatedTestRun(run.id(), run.key(), run.name(), run.environment(),
                run.status(), run.testPlanId());
    }

    @McpTool(
            name = "delete_test_run",
            description = """
                    Permanently delete a test run with all its results and step results. Bug
                    reports found in it are kept but lose their link to the run.
                    Cannot be undone — to stop a run without losing its record, use
                    complete_test_run with ABORTED instead.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, idempotentHint = false))
    @Transactional
    public McpDtos.Deleted deleteTestRun(
            @McpToolParam(description = "Test run UUID or key, e.g. PROJ-Run-7") String runIdOrKey) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        UUID runId = McpRunReferences.resolve(testRunRepository, caller.projectId(), runIdOrKey);
        String key = testRunService.findById(caller.projectId(), runId).key();
        testRunService.delete(caller.projectId(), runId, caller.userId());
        return new McpDtos.Deleted("TEST_RUN", runId, key);
    }
}
