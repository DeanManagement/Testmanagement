package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.TestResultResponse;
import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.project.internal.service.TestRunCaseService;
import com.deanmanagement.testmanagement.project.internal.service.TestRunService;
import com.deanmanagement.testmanagement.shared.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Changing which cases an open run holds, and their order (TM21, TM22). Each returns the run as
 * {@code get_test_run} shows it, so the agent sees the result without a second call.
 */
@Service
@InToolGroup(McpToolGroup.EXECUTION)
@RequiredArgsConstructor
public class TestRunCaseTools {

    private static final int MAX_CASES = 200;

    private final McpCallerContext callerContext;
    private final TestRunRepository testRunRepository;
    private final TestRunService testRunService;
    private final TestRunCaseService runCaseService;
    private final TestRunReadTools readTools;
    private final McpWriteThrottle writeThrottle;

    @McpTool(
            name = "add_test_cases_to_run",
            description = """
                    Add test cases to an existing PLANNED or IN_PROGRESS run. Each gets PENDING
                    results at the end of the run, exactly as if the run had been created with it
                    (one per parameter set for a parameterized case). Cases already in the run are
                    skipped. A COMPLETED or ABORTED run is refused: it must be reopened by a human.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.TestRunDetail addTestCasesToRun(
            @McpToolParam(description = "Test run UUID or key, e.g. PROJ-Run-7") String runIdOrKey,
            @McpToolParam(description = "UUIDs of the test cases to add, at most 200") List<UUID> testCaseIds) {

        var caller = callerContext.requireWriter();
        UUID runId = resolveRun(caller.projectId(), runIdOrKey);
        Set<UUID> ids = requireIds(testCaseIds);
        writeThrottle.recordWrite(caller.apiKeyId());
        return refusalsAsToolErrors(() ->
                runCaseService.addTestCases(caller.projectId(), runId, ids, caller.userId()), runId);
    }

    @McpTool(
            name = "remove_test_cases_from_run",
            description = """
                    Take test cases out of a PLANNED or IN_PROGRESS run, every result of each.
                    Refused if any of them already has a recorded result (anything but PENDING):
                    removing it would discard what a tester recorded, which only a human may decide
                    in the app. A COMPLETED or ABORTED run is refused too.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, idempotentHint = false))
    @Transactional
    public McpDtos.TestRunDetail removeTestCasesFromRun(
            @McpToolParam(description = "Test run UUID or key, e.g. PROJ-Run-7") String runIdOrKey,
            @McpToolParam(description = "UUIDs of the test cases to remove, at most 200") List<UUID> testCaseIds) {

        var caller = callerContext.requireWriter();
        UUID runId = resolveRun(caller.projectId(), runIdOrKey);
        Set<UUID> ids = requireIds(testCaseIds);
        writeThrottle.recordWrite(caller.apiKeyId());
        return refusalsAsToolErrors(() ->
                runCaseService.removeTestCases(caller.projectId(), runId, ids, false, caller.userId()), runId);
    }

    @McpTool(
            name = "reorder_test_run_cases",
            description = """
                    Set the order in which a PLANNED or IN_PROGRESS run's results are executed.
                    resultIds are result ids from get_test_run (not test case ids): the ones you
                    name come first, in your order, and the others follow in their current order —
                    so to move one result to the top, pass just its id.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.TestRunDetail reorderTestRunCases(
            @McpToolParam(description = "Test run UUID or key, e.g. PROJ-Run-7") String runIdOrKey,
            @McpToolParam(description = "Result ids in the order they should come first") List<UUID> resultIds) {

        var caller = callerContext.requireWriter();
        UUID runId = resolveRun(caller.projectId(), runIdOrKey);
        if (resultIds == null || resultIds.isEmpty()) {
            throw new McpToolException("resultIds is required.");
        }
        writeThrottle.recordWrite(caller.apiKeyId());

        List<UUID> current = testRunService.findById(caller.projectId(), runId).results().stream()
                .map(TestResultResponse::id).toList();
        LinkedHashSet<UUID> order = new LinkedHashSet<>(resultIds);
        List<UUID> unknown = order.stream().filter(id -> !current.contains(id)).toList();
        if (!unknown.isEmpty()) {
            throw new McpToolException("Not results of this run: " + unknown
                    + ". Use the result ids from get_test_run, not test case ids.");
        }
        order.addAll(current);
        return refusalsAsToolErrors(() ->
                runCaseService.reorder(caller.projectId(), runId, List.copyOf(order), caller.userId()), runId);
    }

    private UUID resolveRun(UUID projectId, String runIdOrKey) {
        return McpRunReferences.resolve(testRunRepository, projectId, runIdOrKey);
    }

    private static Set<UUID> requireIds(List<UUID> testCaseIds) {
        if (testCaseIds == null || testCaseIds.isEmpty()) {
            throw new McpToolException("testCaseIds is required.");
        }
        if (testCaseIds.size() > MAX_CASES) {
            throw new McpToolException("At most " + MAX_CASES + " test cases per call.");
        }
        return new LinkedHashSet<>(testCaseIds);
    }

    /** A refusal (closed run, recorded results) is the agent's to read, not a server error. */
    private McpDtos.TestRunDetail refusalsAsToolErrors(Runnable change, UUID runId) {
        try {
            change.run();
        } catch (ConflictException | IllegalArgumentException e) {
            throw new McpToolException(e.getMessage());
        }
        return readTools.getTestRun(runId.toString(), null);
    }
}
