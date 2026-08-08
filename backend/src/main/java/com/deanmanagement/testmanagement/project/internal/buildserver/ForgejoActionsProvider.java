package com.deanmanagement.testmanagement.project.internal.buildserver;

import com.deanmanagement.testmanagement.project.internal.entity.BuildServerProviderType;
import com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * Forgejo Actions adapter (PRD-024), also covering Gitea and Codeberg. The base URL is the
 * instance root, e.g. {@code https://codeberg.org}; the v1 API prefix is added here.
 *
 * <p>Same dispatch-without-id model as GitHub, but the run listing differs: Forgejo exposes
 * {@code /actions/tasks} at repository scope, not workflow scope, so
 * {@link AbstractActionsProvider} additionally filters candidates by their {@code workflow_id}.
 * There is no single-run endpoint either — a known run is re-found in the recent list, and a run
 * that fell off the first page keeps its last known status rather than guessing.
 */
@Component
public class ForgejoActionsProvider extends AbstractActionsProvider {

    public ForgejoActionsProvider(BuildServerProperties properties, ObjectMapper objectMapper) {
        super(properties, objectMapper);
    }

    @Override
    public BuildServerProviderType type() {
        return BuildServerProviderType.FORGEJO_ACTIONS;
    }

    @Override
    protected String providerName() {
        return "Forgejo";
    }

    @Override
    protected HttpRequest.Builder authenticate(HttpRequest.Builder builder, String token) {
        return builder.header("Authorization", "token " + token);
    }

    @Override
    protected String repoApi(DecryptedConfig config, String repoRef) {
        String[] parts = ownerRepo(repoRef);
        return trimTrailingSlash(config.baseUrl()) + "/api/v1/repos/" + parts[0] + "/" + parts[1];
    }

    @Override
    protected String recentRunsUrl(DecryptedConfig config, StatusQuery query) {
        return repoApi(config, query.repoRef()) + "/actions/tasks";
    }

    /** Forgejo has no single-run endpoint; known runs are re-found in the recent list. */
    @Override
    protected String runUrl(DecryptedConfig config, String repoRef, String externalRunId) {
        return null;
    }

    @Override
    public void testConnection(DecryptedConfig config) {
        getJson(config, trimTrailingSlash(config.baseUrl()) + "/api/v1/user");
    }

    /**
     * Forgejo ships workflow listing on newer releases only; on older ones the 404 surfaces as a
     * clear upstream error and the admin enters the workflow file manually.
     */
    @Override
    public List<DiscoveredWorkflow> discover(DecryptedConfig config, String repoRef) {
        JsonNode body = getJson(config, repoApi(config, repoRef) + "/actions/workflows");
        JsonNode workflows = body.has("workflows") ? body.get("workflows") : body;
        if (workflows == null || !workflows.isArray()) {
            throw new UpstreamServiceException("Forgejo returned an unexpected workflow list");
        }
        List<DiscoveredWorkflow> discovered = new ArrayList<>();
        for (JsonNode workflow : workflows) {
            String path = text(workflow, "path");
            String name = text(workflow, "name");
            if (path == null && name == null) {
                continue;
            }
            String source = path != null ? path : name;
            String file = source.substring(source.lastIndexOf('/') + 1);
            discovered.add(new DiscoveredWorkflow(name != null ? name : file, repoRef, file, null));
        }
        return discovered;
    }
}
