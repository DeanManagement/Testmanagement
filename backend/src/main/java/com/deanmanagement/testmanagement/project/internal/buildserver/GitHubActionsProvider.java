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
 * GitHub Actions adapter (PRD-024). The base URL is the API root — {@code https://api.github.com}
 * for github.com, {@code https://ghe.example.com/api/v3} for GitHub Enterprise Server.
 *
 * <p>Dispatch mechanics and run correlation live in {@link AbstractActionsProvider}; this class
 * contributes GitHub's URL shapes, its bearer auth, and workflow discovery.
 */
@Component
public class GitHubActionsProvider extends AbstractActionsProvider {

    public GitHubActionsProvider(BuildServerProperties properties, ObjectMapper objectMapper) {
        super(properties, objectMapper);
    }

    @Override
    public BuildServerProviderType type() {
        return BuildServerProviderType.GITHUB_ACTIONS;
    }

    @Override
    protected String providerName() {
        return "GitHub";
    }

    @Override
    protected HttpRequest.Builder authenticate(HttpRequest.Builder builder, String token) {
        return builder.header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", "2022-11-28");
    }

    @Override
    protected String repoApi(DecryptedConfig config, String repoRef) {
        String[] parts = ownerRepo(repoRef);
        return trimTrailingSlash(config.baseUrl()) + "/repos/" + parts[0] + "/" + parts[1];
    }

    /** Workflow-scoped listing, so correlation never has to match on the workflow path itself. */
    @Override
    protected String recentRunsUrl(DecryptedConfig config, StatusQuery query) {
        return repoApi(config, query.repoRef()) + "/actions/workflows/"
                + encodePath(query.workflowRef()) + "/runs";
    }

    @Override
    protected String runUrl(DecryptedConfig config, String repoRef, String externalRunId) {
        return repoApi(config, repoRef) + "/actions/runs/" + encodePath(externalRunId);
    }

    @Override
    public void testConnection(DecryptedConfig config) {
        // Proves the token authenticates; repo visibility is checked per workflow at trigger time.
        getJson(config, trimTrailingSlash(config.baseUrl()) + "/user");
    }

    @Override
    public List<DiscoveredWorkflow> discover(DecryptedConfig config, String repoRef) {
        JsonNode body = getJson(config, repoApi(config, repoRef) + "/actions/workflows");
        JsonNode workflows = body.get("workflows");
        if (workflows == null || !workflows.isArray()) {
            throw new UpstreamServiceException("GitHub returned an unexpected workflow list");
        }
        List<DiscoveredWorkflow> discovered = new ArrayList<>();
        for (JsonNode workflow : workflows) {
            String path = text(workflow, "path");
            if (path == null) {
                continue;
            }
            // ".github/workflows/tests.yml" -> "tests.yml": the dispatch endpoint takes the file name.
            String file = path.substring(path.lastIndexOf('/') + 1);
            discovered.add(new DiscoveredWorkflow(text(workflow, "name"), repoRef, file, null));
        }
        return discovered;
    }
}
