package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CloneTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.project.internal.service.TestRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Housekeeping on a run that is not recording a result: rename it, re-file it, run it again.
 *
 * <p>Separate from {@link TestRunWriteTools} and the recording tools, which own the execute-a-run
 * loop. Neither tool here
 * can change a run's status — closing is {@code complete_test_run}'s job and reopening stays a
 * human decision (PRD-027 §2).
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

    @McpTool(
            name = "update_test_run",
            description = """
                    Rename a test run, change its environment, or file it under a test plan. Only
                    the fields you pass are changed. To CLEAR the environment pass an empty
                    string "".
                    This cannot change the run's status: use complete_test_run to close a run.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.UpdatedTestRun updateTestRun(
            @McpToolParam(description = "Test run UUID or key, e.g. PROJ-Run-7") String runIdOrKey,
            @McpToolParam(description = "New name, max 255 characters", required = false) String name,
            @McpToolParam(description = "New environment, e.g. staging; \"\" clears it",
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
                    result PENDING — the way to re-test after a fix, since a completed run cannot
                    be reopened from here. Results, comments and the test plan are not copied.
                    You are recorded as the executor of the new run.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.CreatedTestRun cloneTestRun(
            @McpToolParam(description = "UUID or key of the run to copy, e.g. PROJ-Run-7")
            String runIdOrKey,
            @McpToolParam(description = "Name of the new run, max 255 characters") String name,
            @McpToolParam(description = "Environment of the new run, e.g. staging", required = false)
            String environment) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());

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
}
