package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.StepResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateStepResultRequest;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Recording a run one step at a time. Separate from {@link TestResultRecordingTools} because the
 * two are alternatives for a given case, not companions: here the case's own status is derived
 * from its steps rather than set.
 */
@Service
@InToolGroup(McpToolGroup.EXECUTION)
@RequiredArgsConstructor
public class TestStepRecordingTools {

    private final McpCallerContext callerContext;
    private final TestRunService testRunService;
    private final TestRunRepository testRunRepository;
    private final McpRunSupport runSupport;
    private final McpWriteThrottle writeThrottle;
    private final McpValidator validator;

    @McpTool(
            name = "record_step_result",
            description = """
                    Record the outcome of ONE STEP of a test case in a run — use this instead of
                    record_test_result when the case has steps and you want the record to show
                    which step failed.
                    status: PASSED | FAILED | BLOCKED | SKIPPED.
                    stepNumber is the 1-based position of the step, in the order get_test_case
                    lists them. Put what you observed at that step in actualResult.
                    Do not also set the case's overall status: it is derived from its steps (the
                    worst one wins, and it stays PENDING until every step is recorded). The response
                    lists every step of the result so you can see what is left.
                    Identify the result by testCaseId, or by resultId when the case is
                    parameterized. Recording the first step on a PLANNED run starts it.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.RecordedStepResult recordStepResult(
            @McpToolParam(description = "Test run UUID or key, e.g. PROJ-Run-7") String runIdOrKey,
            @McpToolParam(description = "1-based position of the step within the test case")
            Integer stepNumber,
            @McpToolParam(description = "Outcome: PASSED | FAILED | BLOCKED | SKIPPED")
            TestResultStatus status,
            @McpToolParam(description = "Test case UUID; omit if you pass resultId", required = false)
            UUID testCaseId,
            @McpToolParam(description = "Result UUID from get_test_run; use when testCaseId is "
                    + "ambiguous", required = false) UUID resultId,
            @McpToolParam(description = "What you observed at this step", required = false)
            String actualResult) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());

        if (status == null || status == TestResultStatus.PENDING) {
            throw new McpToolException("status is required: PASSED, FAILED, BLOCKED or SKIPPED.");
        }
        if (testCaseId == null && resultId == null) {
            throw new McpToolException("Pass either testCaseId or resultId.");
        }

        UUID runId = McpRunReferences.resolve(testRunRepository, caller.projectId(), runIdOrKey);
        TestRunResponse run = testRunService.findById(caller.projectId(), runId);
        McpRunSupport.requireOpen(run);

        TestResultResponse result = McpRunSupport.resolveResult(run, resultId, testCaseId);
        if (result == null) {
            throw new McpToolException("Test case " + testCaseId + " has no result in run "
                    + run.key() + ". Call get_test_run to see its results.");
        }
        StepResultResponse step = stepAt(result, stepNumber);

        TestRunStatus runStatus = runSupport.startIfPlanned(caller, run);
        // Absent actualResult means "leave alone", not "clear" — a re-record must not erase the
        // evidence, for the reason record_test_result gives about its comment.
        var update = new UpdateStepResultRequest(status,
                actualResult == null ? step.actualResult() : actualResult);
        validator.validate(update);
        testRunService.updateStepResult(caller.projectId(), runId, result.id(), step.id(), update);

        TestResultResponse recorded = testRunService.findById(caller.projectId(), runId).results()
                .stream().filter(r -> r.id().equals(result.id())).findFirst().orElseThrow();
        List<StepResultResponse> ordered = orderedSteps(recorded);
        List<McpDtos.StepOutcome> steps = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            StepResultResponse s = ordered.get(index);
            steps.add(new McpDtos.StepOutcome(index + 1, s.action(), s.status(), s.actualResult()));
        }
        return new McpDtos.RecordedStepResult(recorded.id(), recorded.testCaseId(),
                recorded.testCaseTitle(), recorded.status(), runStatus, steps);
    }

    private static StepResultResponse stepAt(TestResultResponse result, Integer stepNumber) {
        List<StepResultResponse> steps = orderedSteps(result);
        if (steps.isEmpty()) {
            throw new McpToolException("\"" + result.testCaseTitle() + "\" has no steps in this "
                    + "run. Record its outcome with record_test_result instead.");
        }
        if (stepNumber == null || stepNumber < 1 || stepNumber > steps.size()) {
            throw new McpToolException("stepNumber must be between 1 and " + steps.size()
                    + " for \"" + result.testCaseTitle() + "\".");
        }
        return steps.get(stepNumber - 1);
    }

    private static List<StepResultResponse> orderedSteps(TestResultResponse result) {
        if (result.stepResults() == null) {
            return List.of();
        }
        return result.stepResults().stream()
                .sorted(Comparator.comparingInt(StepResultResponse::orderIndex))
                .toList();
    }
}
