package com.deanmanagement.testmanagement.project.internal.buildserver;

import com.deanmanagement.testmanagement.project.internal.entity.BuildServerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.BuildServerProviderType;
import com.deanmanagement.testmanagement.project.internal.entity.PipelineRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One build server's API, reduced to the operations the tool needs (PRD-024 §3.2). Adapters
 * receive the config with the token already decrypted and must not persist or log it.
 *
 * <p>Implementations throw {@link com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException}
 * for anything the caller cannot fix — transport failure, auth rejection, rate limiting, malformed
 * responses — so the service layer has a single failure mode to handle.
 */
public interface BuildServerProvider {

    BuildServerProviderType type();

    /**
     * Starts the pipeline. {@code spec.parameters()} already contains the injected
     * {@code TM_*} correlation variables merged over the workflow defaults and the tester's
     * overrides. Providers whose dispatch API rejects undeclared inputs (GitHub/Forgejo) retry
     * once without parameters rather than failing the trigger.
     *
     * <p>May legitimately return a null external run id: the Actions dispatch endpoints return
     * 204 with no run reference. The poller correlates such runs later via {@link #fetchStatus}.
     */
    TriggerResult trigger(DecryptedConfig config, TriggerSpec spec);

    /**
     * Current status of a triggered run. When {@code query.externalRunId()} is null the provider
     * attempts correlation first (Actions: newest {@code workflow_dispatch} run of the workflow
     * and ref created at/after the trigger, preferring one whose display name carries the
     * pipeline-run id via the documented {@code run-name} convention).
     */
    StatusResult fetchStatus(DecryptedConfig config, StatusQuery query);

    /** Verifies credentials without side effects, for the config form's test button. */
    void testConnection(DecryptedConfig config);

    /**
     * Lists what an admin can pick as a workflow: workflow files (Actions), jobs (Jenkins),
     * repositories (Woodpecker), branches (GitLab). A UX assist only — manual entry always works.
     *
     * @throws UnsupportedOperationException when the provider has nothing useful to list.
     */
    default List<DiscoveredWorkflow> discover(DecryptedConfig config, String repoRef) {
        throw new UnsupportedOperationException(type() + " does not support workflow discovery");
    }

    /** A config paired with its plaintext token, assembled per call and never stored. */
    record DecryptedConfig(BuildServerConfig config, String token) {
        public String baseUrl() {
            return config.getBaseUrl();
        }
    }

    record TriggerSpec(
            String repoRef,
            String workflowRef,
            String ref,
            Map<String, String> parameters,
            java.util.UUID pipelineRunId
    ) {
    }

    record TriggerResult(PipelineRunStatus status, String externalRunId, String externalUrl) {
    }

    record StatusQuery(
            String repoRef,
            String workflowRef,
            String externalRunId,
            String triggeredRef,
            java.util.UUID pipelineRunId,
            Instant triggeredAt
    ) {
    }

    /**
     * @param status null means "no verdict this pass — keep the stored status" (an Actions run
     *        not yet visible after dispatch, or one that fell off the listing page)
     * @param externalRunId may upgrade a previously null id once correlation succeeds; callers
     *        keep the stored value when this is null
     */
    record StatusResult(PipelineRunStatus status, String externalRunId, String externalUrl) {
    }

    record DiscoveredWorkflow(String name, String repoRef, String workflowRef, String defaultRef) {
    }
}
