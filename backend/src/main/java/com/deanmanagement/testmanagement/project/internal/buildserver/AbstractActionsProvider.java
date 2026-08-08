package com.deanmanagement.testmanagement.project.internal.buildserver;

import com.deanmanagement.testmanagement.project.internal.entity.PipelineRunStatus;
import com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared mechanics for GitHub-style Actions providers (GitHub, Forgejo/Gitea), whose
 * {@code workflow_dispatch} endpoint answers 204 <em>without a run id</em> — the one genuinely
 * awkward spot in PRD-024.
 *
 * <p>Two consequences are handled here. First, dispatch inputs must be declared by the workflow
 * or the server answers 422, so a rejected dispatch is retried once without inputs — the trigger
 * still works, only push-back correlation via {@code TM_PIPELINE_RUN_ID} is lost. Second, the run
 * id is recovered afterwards by listing recent {@code workflow_dispatch} runs of that workflow
 * and ref: a run whose display name contains the pipeline-run id (the documented
 * {@code run-name} convention) is an exact match, otherwise the newest plausible candidate is
 * taken.
 */
abstract class AbstractActionsProvider extends HttpBuildProviderSupport implements BuildServerProvider {

    /** Clock skew allowed between this host and the build server when matching by creation time. */
    private static final Duration CORRELATION_SKEW = Duration.ofSeconds(60);
    private static final int RUN_PAGE_SIZE = 30;

    protected AbstractActionsProvider(BuildServerProperties properties, ObjectMapper objectMapper) {
        super(properties, objectMapper);
    }

    /** API root for a repository, e.g. {@code {base}/repos/{owner}/{repo}}. */
    protected abstract String repoApi(DecryptedConfig config, String repoRef);

    /** URL listing recent runs to correlate a dispatch against. */
    protected abstract String recentRunsUrl(DecryptedConfig config, StatusQuery query);

    /** URL fetching a single run by id, or null when the provider cannot address one directly. */
    protected abstract String runUrl(DecryptedConfig config, String repoRef, String externalRunId);

    @Override
    public TriggerResult trigger(DecryptedConfig config, TriggerSpec spec) {
        if (spec.ref() == null || spec.ref().isBlank()) {
            throw new IllegalArgumentException(providerName()
                    + " requires a branch or tag ref to dispatch a workflow");
        }
        String url = repoApi(config, spec.repoRef()) + "/actions/workflows/"
                + encodePath(requireWorkflowRef(spec.workflowRef())) + "/dispatches";

        HttpResponse<String> response = dispatch(config, url, spec.ref(), spec.parameters());
        if (response.statusCode() == 422) {
            // The workflow does not declare our inputs (or some of the tester's). Retrying bare
            // keeps the trigger usable; correlation then relies on timing rather than run-name.
            response = dispatch(config, url, spec.ref(), Map.of());
            if (response.statusCode() == 422) {
                throw new UpstreamServiceException(providerName() + " rejected the dispatch (HTTP 422)"
                        + " — the workflow must declare a workflow_dispatch trigger");
            }
        }
        // 204, no body, no run id: the poller correlates via fetchStatus.
        return new TriggerResult(PipelineRunStatus.TRIGGERED, null, null);
    }

    private HttpResponse<String> dispatch(DecryptedConfig config, String url, String ref,
                                          Map<String, String> inputs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ref", ref);
        if (!inputs.isEmpty()) {
            payload.put("inputs", inputs);
        }
        HttpRequest request = request(config, url)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(serialize(payload), StandardCharsets.UTF_8))
                .build();
        return sendTolerating(request, config, 422);
    }

    @Override
    public StatusResult fetchStatus(DecryptedConfig config, StatusQuery query) {
        if (query.externalRunId() != null) {
            return fetchKnownRun(config, query);
        }
        return correlate(config, query);
    }

    private StatusResult fetchKnownRun(DecryptedConfig config, StatusQuery query) {
        String url = runUrl(config, query.repoRef(), query.externalRunId());
        if (url != null) {
            JsonNode run = getJson(config, url);
            return new StatusResult(mapRun(run), null, htmlUrl(run));
        }
        // No single-run endpoint: find the run again in the recent list.
        JsonNode match = findById(listRuns(config, query), query.externalRunId());
        if (match == null) {
            // Fell off the first page; keep whatever status we had rather than guessing.
            return new StatusResult(null, null, null);
        }
        return new StatusResult(mapRun(match), null, htmlUrl(match));
    }

    private StatusResult correlate(DecryptedConfig config, StatusQuery query) {
        List<JsonNode> candidates = candidates(config, query);
        JsonNode match = byRunName(candidates, query.pipelineRunId().toString());
        if (match == null) {
            match = newest(candidates);
        }
        if (match == null) {
            // Not visible yet — dispatch processing is asynchronous. Stay TRIGGERED; the poller
            // will try again until the run timeout reaps it.
            return new StatusResult(null, null, null);
        }
        return new StatusResult(mapRun(match), idOf(match), htmlUrl(match));
    }

    private List<JsonNode> candidates(DecryptedConfig config, StatusQuery query) {
        Instant earliest = query.triggeredAt().minus(CORRELATION_SKEW);
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode run : listRuns(config, query)) {
            if (!matchesWorkflow(run, query) || !matchesRef(run, query)) {
                continue;
            }
            Instant created = createdAt(run);
            if (created != null && created.isBefore(earliest)) {
                continue;
            }
            result.add(run);
        }
        return result;
    }

    protected List<JsonNode> listRuns(DecryptedConfig config, StatusQuery query) {
        JsonNode body = getJson(config, recentRunsUrl(config, query) + "?per_page=" + RUN_PAGE_SIZE);
        JsonNode array = body.has("workflow_runs") ? body.get("workflow_runs") : body.get("entries");
        List<JsonNode> runs = new ArrayList<>();
        if (array != null && array.isArray()) {
            array.forEach(runs::add);
        }
        return runs;
    }

    /** Whether a listed run belongs to the queried workflow file. */
    protected boolean matchesWorkflow(JsonNode run, StatusQuery query) {
        String workflow = text(run, "path");
        if (workflow == null) {
            workflow = text(run, "workflow_id");
        }
        if (workflow == null) {
            return true; // Listing was already workflow-scoped, or the field is absent.
        }
        String file = query.workflowRef();
        return workflow.equals(file) || workflow.endsWith("/" + file);
    }

    private boolean matchesRef(JsonNode run, StatusQuery query) {
        String branch = text(run, "head_branch");
        return branch == null || query.triggeredRef() == null || branch.equals(query.triggeredRef());
    }

    private static JsonNode byRunName(List<JsonNode> candidates, String pipelineRunId) {
        for (JsonNode run : candidates) {
            String title = text(run, "display_title");
            String name = text(run, "name");
            if ((title != null && title.contains(pipelineRunId))
                    || (name != null && name.contains(pipelineRunId))) {
                return run;
            }
        }
        return null;
    }

    private JsonNode newest(List<JsonNode> candidates) {
        JsonNode best = null;
        Instant bestCreated = null;
        for (JsonNode run : candidates) {
            Instant created = createdAt(run);
            if (best == null || (created != null && (bestCreated == null || created.isAfter(bestCreated)))) {
                best = run;
                bestCreated = created;
            }
        }
        return best;
    }

    private JsonNode findById(List<JsonNode> runs, String externalRunId) {
        for (JsonNode run : runs) {
            if (externalRunId.equals(idOf(run))) {
                return run;
            }
        }
        return null;
    }

    protected String idOf(JsonNode run) {
        JsonNode id = run.get("id");
        if (id == null || id.isNull()) {
            throw new UpstreamServiceException(providerName() + " run entry is missing an id");
        }
        return id.asString();
    }

    protected String htmlUrl(JsonNode run) {
        String url = text(run, "html_url");
        return url != null ? url : text(run, "url");
    }

    protected Instant createdAt(JsonNode run) {
        String created = text(run, "created_at");
        if (created == null) {
            created = text(run, "created");
        }
        try {
            return created == null ? null : Instant.parse(created);
        } catch (Exception e) {
            return null;
        }
    }

    /** Maps GitHub's status/conclusion pair; Forgejo folds the conclusion into status directly. */
    protected PipelineRunStatus mapRun(JsonNode run) {
        String status = text(run, "status");
        String conclusion = text(run, "conclusion");
        String effective = "completed".equalsIgnoreCase(status) && conclusion != null
                ? conclusion : status;
        if (effective == null) {
            return PipelineRunStatus.PENDING;
        }
        return switch (effective.toLowerCase()) {
            case "queued", "waiting", "pending", "requested", "blocked" -> PipelineRunStatus.PENDING;
            case "in_progress", "running" -> PipelineRunStatus.RUNNING;
            case "success", "completed" -> PipelineRunStatus.SUCCESS;
            case "failure", "timed_out", "startup_failure" -> PipelineRunStatus.FAILED;
            case "cancelled", "canceled", "skipped" -> PipelineRunStatus.CANCELLED;
            default -> PipelineRunStatus.PENDING;
        };
    }

    private static String requireWorkflowRef(String workflowRef) {
        if (workflowRef == null || workflowRef.isBlank()) {
            throw new IllegalArgumentException(
                    "An Actions workflow needs a workflow file reference, e.g. tests.yml");
        }
        return workflowRef;
    }
}
