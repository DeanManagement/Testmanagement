package com.deanmanagement.testmanagement.project.internal.buildserver;

import com.deanmanagement.testmanagement.project.internal.entity.BuildServerProviderType;
import com.deanmanagement.testmanagement.project.internal.entity.PipelineRunStatus;
import com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GitLab CI adapter over the v4 REST API (PRD-024). Works against gitlab.com and self-hosted
 * instances. GitLab has no workflow-file concept — a trigger starts the pipeline of a ref — so
 * {@code workflowRef} is unused and discovery lists the project's branches instead.
 *
 * <p>Authentication uses the {@code PRIVATE-TOKEN} header; a project access token scoped to
 * {@code api} on the one project is the right credential.
 */
@Component
public class GitLabCiProvider extends HttpBuildProviderSupport implements BuildServerProvider {

    private static final int DISCOVER_PAGE_SIZE = 50;

    public GitLabCiProvider(BuildServerProperties properties, ObjectMapper objectMapper) {
        super(properties, objectMapper);
    }

    @Override
    public BuildServerProviderType type() {
        return BuildServerProviderType.GITLAB_CI;
    }

    @Override
    protected String providerName() {
        return "GitLab";
    }

    @Override
    protected HttpRequest.Builder authenticate(HttpRequest.Builder builder, String token) {
        return builder.header("PRIVATE-TOKEN", token);
    }

    @Override
    public TriggerResult trigger(DecryptedConfig config, TriggerSpec spec) {
        List<Map<String, String>> variables = new ArrayList<>();
        spec.parameters().forEach((key, value) -> variables.add(Map.of("key", key, "value", value)));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ref", requireRef(spec.ref()));
        if (!variables.isEmpty()) {
            payload.put("variables", variables);
        }

        JsonNode pipeline = postJson(config, projectApi(config, spec.repoRef()) + "/pipeline", payload);
        return new TriggerResult(mapStatus(text(pipeline, "status")),
                requireId(pipeline), text(pipeline, "web_url"));
    }

    @Override
    public StatusResult fetchStatus(DecryptedConfig config, StatusQuery query) {
        if (query.externalRunId() == null) {
            // Trigger always returns an id, so a null here means the trigger itself failed.
            throw new UpstreamServiceException("GitLab pipeline id is missing; cannot poll status");
        }
        JsonNode pipeline = getJson(config, projectApi(config, query.repoRef())
                + "/pipelines/" + encodePath(query.externalRunId()));
        return new StatusResult(mapStatus(text(pipeline, "status")), null, text(pipeline, "web_url"));
    }

    @Override
    public void testConnection(DecryptedConfig config) {
        // The version endpoint requires a valid token but no particular project, which is all a
        // global server config can promise — repo access is checked per workflow at trigger time.
        getJson(config, trimTrailingSlash(config.baseUrl()) + "/api/v4/version");
    }

    /** GitLab has no workflow files; the useful pick-list is the project's branches. */
    @Override
    public List<DiscoveredWorkflow> discover(DecryptedConfig config, String repoRef) {
        JsonNode branches = getJson(config, projectApi(config, repoRef)
                + "/repository/branches?per_page=" + DISCOVER_PAGE_SIZE);
        if (!branches.isArray()) {
            throw new UpstreamServiceException("GitLab returned an unexpected branch list");
        }
        List<DiscoveredWorkflow> discovered = new ArrayList<>();
        for (JsonNode branch : branches) {
            String name = text(branch, "name");
            if (name != null) {
                discovered.add(new DiscoveredWorkflow("Pipeline on " + name, repoRef, null, name));
            }
        }
        return discovered;
    }

    private String projectApi(DecryptedConfig config, String repoRef) {
        // GitLab addresses projects by URL-encoded path, so "group/sub/project" becomes
        // "group%2Fsub%2Fproject"; a numeric id passes through unchanged.
        return trimTrailingSlash(config.baseUrl()) + "/api/v4/projects/" + encodePath(repoRef);
    }

    private static String requireRef(String ref) {
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("GitLab requires a branch or tag ref to trigger a pipeline");
        }
        return ref;
    }

    private String requireId(JsonNode pipeline) {
        JsonNode id = pipeline.get("id");
        if (id == null || id.isNull()) {
            throw new UpstreamServiceException("GitLab pipeline response is missing an id");
        }
        return id.asString();
    }

    private static PipelineRunStatus mapStatus(String status) {
        if (status == null) {
            return PipelineRunStatus.PENDING;
        }
        return switch (status.toLowerCase()) {
            case "created", "waiting_for_resource", "preparing", "pending", "scheduled", "manual" ->
                    PipelineRunStatus.PENDING;
            case "running" -> PipelineRunStatus.RUNNING;
            case "success" -> PipelineRunStatus.SUCCESS;
            case "failed" -> PipelineRunStatus.FAILED;
            case "canceled", "skipped" -> PipelineRunStatus.CANCELLED;
            default -> PipelineRunStatus.PENDING;
        };
    }
}
