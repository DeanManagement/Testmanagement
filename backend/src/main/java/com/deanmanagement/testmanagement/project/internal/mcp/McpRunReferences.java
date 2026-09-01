package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;

import java.util.UUID;

/**
 * Resolves a run reference that may be either a UUID or a run key (PRD-027 §8.3).
 *
 * <p>Every run response leads with {@code key} — {@code WEB-Run-7} — and {@code create_test_run}'s
 * description tells the agent that is what to quote back to a human. Then {@code get_test_run}
 * refused it and demanded the UUID. Showing an identifier prominently and then rejecting it is a
 * small cruelty that costs a round trip every time, and it was inconsistent besides:
 * {@code get_test_case} and {@code update_test_case} have taken {@code idOrKey} since PRD-025.
 *
 * <p>Reported from a real session, where the cost was an extra {@code get_test_run} purely to
 * translate a key the agent already had into an id it did not.
 */
final class McpRunReferences {

    private McpRunReferences() {
    }

    /**
     * @param reference a run UUID or a run key; the key form is matched case-sensitively, as keys
     *                  are generated rather than typed
     * @return the run's id, never another project's — the key lookup is project-scoped, because run
     *         keys are unique instance-wide and an unscoped one would resolve a stranger's run
     */
    static UUID resolve(TestRunRepository runs, UUID projectId, String reference) {
        if (reference == null || reference.isBlank()) {
            throw new McpToolException("A test run id or key is required, e.g. the key from "
                    + "create_test_run.");
        }
        String trimmed = reference.trim();
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException notAUuid) {
            return runs.findByKeyAndProjectId(trimmed, projectId)
                    .map(TestRun::getId)
                    .orElseThrow(() -> new McpToolException("No test run " + trimmed
                            + " in this project. Call list_test_runs to see what there is."));
        }
    }
}
