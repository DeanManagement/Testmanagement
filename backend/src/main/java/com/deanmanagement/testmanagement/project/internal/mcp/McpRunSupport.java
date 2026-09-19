package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.TestResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.service.TestRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What the run tools share: moving a run between states, finding the result a call is about, and
 * counting outcomes. Kept in one place so {@link TestRunWriteTools},
 * {@link TestResultRecordingTools} and {@link TestStepRecordingTools} cannot drift on when a run
 * starts or which result an ambiguous test case id means.
 */
@Component
@RequiredArgsConstructor
class McpRunSupport {

    private final TestRunService testRunService;

    /** Results of a closed run are the signed-off record; reopening is a human call (PRD-027 §2). */
    static void requireOpen(TestRunResponse run) {
        if (run.status() == TestRunStatus.COMPLETED || run.status() == TestRunStatus.ABORTED) {
            throw new McpToolException("Run " + run.key() + " is " + run.status()
                    + ", so its results are final. Create a new run to re-test.");
        }
    }

    /**
     * Status-only update.
     *
     * <p>Name, environment and plan are passed as null rather than echoed back from the response:
     * {@code TestRunService.update} treats null as "unchanged" (PRD-027 §3.2), so an agent closing
     * a run cannot revert a rename a human made while it was still executing.
     */
    TestRunResponse transition(McpCallerContext.Caller caller, TestRunResponse run,
                                       TestRunStatus target) {
        return transition(caller, run, target, null);
    }

    /** {@code abortReason} is required when {@code target} is ABORTED. */
    TestRunResponse transition(McpCallerContext.Caller caller, TestRunResponse run,
                               TestRunStatus target, String abortReason) {
        return testRunService.update(caller.projectId(), run.id(),
                new UpdateTestRunRequest(null, null, target, null, null, null, null, abortReason), caller.userId());
    }

    /**
     * Finds the result this call is about, or null when the case is not in the run.
     *
     * <p>The ambiguity refusal is the point. A parameterized case (PRD-015) expands into one result
     * per parameter set, so a test case id can name three rows; picking one would silently record
     * an outcome against the wrong data set. The message lists the candidates so the agent can
     * retry with a resultId, which is why {@code get_test_run} publishes them.
     */
    static TestResultResponse resolveResult(TestRunResponse run, UUID resultId, UUID testCaseId) {
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
    TestRunStatus startIfPlanned(McpCallerContext.Caller caller, TestRunResponse run) {
        if (run.status() != TestRunStatus.PLANNED) {
            return run.status();
        }
        return transition(caller, run, TestRunStatus.IN_PROGRESS).status();
    }

    static McpDtos.CompletedTestRun counts(TestRunResponse run) {
        List<TestResultResponse> results = run.results() == null ? List.of() : run.results();
        return new McpDtos.CompletedTestRun(run.id(), run.key(), run.status(), results.size(),
                count(results, TestResultStatus.PASSED),
                count(results, TestResultStatus.FAILED),
                count(results, TestResultStatus.BLOCKED),
                count(results, TestResultStatus.SKIPPED),
                count(results, TestResultStatus.PENDING));
    }

    static int count(List<TestResultResponse> results, TestResultStatus status) {
        return (int) results.stream().filter(r -> r.status() == status).count();
    }
}
