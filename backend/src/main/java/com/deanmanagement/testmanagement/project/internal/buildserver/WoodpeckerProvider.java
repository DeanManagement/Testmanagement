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
 * Woodpecker CI adapter (PRD-024). Repositories are addressed by their numeric id; a trigger
 * creates a pipeline on a branch with free-form variables, and the response carries the pipeline
 * number directly — the clean case, like GitLab.
 *
 * <p>Authentication is a bearer token from the user's Woodpecker settings.
 */
@Component
public class WoodpeckerProvider extends HttpBuildProviderSupport implements BuildServerProvider {

    public WoodpeckerProvider(BuildServerProperties properties, ObjectMapper objectMapper) {
        super(properties, objectMapper);
    }

    @Override
    public BuildServerProviderType type() {
        return BuildServerProviderType.WOODPECKER;
    }

    @Override
    protected String providerName() {
        return "Woodpecker";
    }

    @Override
    protected HttpRequest.Builder authenticate(HttpRequest.Builder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    @Override
    public TriggerResult trigger(DecryptedConfig config, TriggerSpec spec) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (spec.ref() != null && !spec.ref().isBlank()) {
            payload.put("branch", spec.ref());
        }
        if (!spec.parameters().isEmpty()) {
            payload.put("variables", spec.parameters());
        }
        JsonNode pipeline = postJson(config, repoApi(config, spec.repoRef()) + "/pipelines", payload);
        String number = requireNumber(pipeline);
        return new TriggerResult(mapStatus(text(pipeline, "status")), number,
                pipelineUrl(config, spec.repoRef(), number));
    }

    @Override
    public StatusResult fetchStatus(DecryptedConfig config, StatusQuery query) {
        if (query.externalRunId() == null) {
            throw new UpstreamServiceException("Woodpecker pipeline number is missing; cannot poll status");
        }
        JsonNode pipeline = getJson(config, repoApi(config, query.repoRef())
                + "/pipelines/" + encodePath(query.externalRunId()));
        return new StatusResult(mapStatus(text(pipeline, "status")), null,
                pipelineUrl(config, query.repoRef(), query.externalRunId()));
    }

    @Override
    public void testConnection(DecryptedConfig config) {
        getJson(config, trimTrailingSlash(config.baseUrl()) + "/api/user");
    }

    /** Lists the repositories the token can see; the admin picks one as the workflow's repo. */
    @Override
    public List<DiscoveredWorkflow> discover(DecryptedConfig config, String repoRef) {
        JsonNode repos = getJson(config, trimTrailingSlash(config.baseUrl()) + "/api/user/repos");
        if (!repos.isArray()) {
            throw new UpstreamServiceException("Woodpecker returned an unexpected repository list");
        }
        List<DiscoveredWorkflow> discovered = new ArrayList<>();
        for (JsonNode repo : repos) {
            JsonNode id = repo.get("id");
            String fullName = text(repo, "full_name");
            if (id != null && !id.isNull() && fullName != null) {
                discovered.add(new DiscoveredWorkflow(fullName, id.asString(), null,
                        text(repo, "default_branch")));
            }
        }
        return discovered;
    }

    private String repoApi(DecryptedConfig config, String repoRef) {
        return trimTrailingSlash(config.baseUrl()) + "/api/repos/" + encodePath(repoRef);
    }

    private String pipelineUrl(DecryptedConfig config, String repoRef, String number) {
        return trimTrailingSlash(config.baseUrl()) + "/repos/" + encodePath(repoRef)
                + "/pipeline/" + encodePath(number);
    }

    private String requireNumber(JsonNode pipeline) {
        JsonNode number = pipeline.get("number");
        if (number == null || number.isNull()) {
            throw new UpstreamServiceException("Woodpecker pipeline response is missing a number");
        }
        return number.asString();
    }

    private static PipelineRunStatus mapStatus(String status) {
        if (status == null) {
            return PipelineRunStatus.PENDING;
        }
        return switch (status.toLowerCase()) {
            case "pending", "blocked", "declined", "created" -> PipelineRunStatus.PENDING;
            case "running", "started" -> PipelineRunStatus.RUNNING;
            case "success" -> PipelineRunStatus.SUCCESS;
            case "failure", "error" -> PipelineRunStatus.FAILED;
            case "killed", "canceled", "cancelled", "skipped" -> PipelineRunStatus.CANCELLED;
            default -> PipelineRunStatus.PENDING;
        };
    }
}
