package com.deanmanagement.testmanagement.project.internal.issuetracker;

import com.deanmanagement.testmanagement.project.internal.entity.IssueState;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
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
 * GitLab adapter over the v4 REST API (PRD-010). Works against gitlab.com and self-hosted
 * instances; the base URL is SSRF-validated before a config is stored.
 *
 * <p>Authentication uses the {@code PRIVATE-TOKEN} header, which accepts both personal and project
 * access tokens — the latter being the better choice here, since it can be scoped to a single
 * project with only {@code api} rights.
 */
@Component
public class GitLabIssueProvider extends HttpIssueProviderSupport implements IssueTrackerProvider {

    private static final int SEARCH_PAGE_SIZE = 20;
    private static final int MAX_TITLE_LENGTH = 500;

    public GitLabIssueProvider(IssueTrackerProperties properties, ObjectMapper objectMapper) {
        super(properties, objectMapper);
    }

    @Override
    public IssueTrackerProviderType type() {
        return IssueTrackerProviderType.GITLAB;
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
    public List<Issue> search(DecryptedConfig config, String query) {
        String url = projectApi(config) + "/issues?scope=all&per_page=" + SEARCH_PAGE_SIZE
                + "&search=" + encode(query);
        JsonNode body = getJson(config, url);
        if (!body.isArray()) {
            throw new UpstreamServiceException("GitLab returned an unexpected search response");
        }
        List<Issue> issues = new ArrayList<>();
        for (JsonNode node : body) {
            issues.add(toIssue(config, node));
        }
        return issues;
    }

    @Override
    public Issue create(DecryptedConfig config, IssueDraft draft) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", draft.title());
        payload.put("description", draft.body());
        return toIssue(config, postJson(config, projectApi(config) + "/issues", payload));
    }

    @Override
    public Issue get(DecryptedConfig config, String externalId) {
        String iid = issueNumber(externalId);
        return toIssue(config, getJson(config, projectApi(config) + "/issues/" + encodePath(iid)));
    }

    @Override
    public void testConnection(DecryptedConfig config) {
        // Fetching the project itself proves both that the token is valid and that it can see the
        // configured project — a token that authenticates but lacks access would otherwise only
        // fail later, at the first search.
        getJson(config, projectApi(config));
    }

    private String projectApi(DecryptedConfig config) {
        // GitLab addresses projects by URL-encoded path, so "group/sub/project" becomes
        // "group%2Fsub%2Fproject"; a numeric id passes through unchanged.
        return trimTrailingSlash(config.baseUrl()) + "/api/v4/projects/" + encodePath(config.projectRef());
    }

    private Issue toIssue(DecryptedConfig config, JsonNode node) {
        JsonNode iid = node.get("iid");
        if (iid == null || iid.isNull()) {
            throw new UpstreamServiceException("GitLab issue response is missing an iid");
        }
        String webUrl = text(node, "web_url");
        return new Issue(
                config.projectRef() + "#" + iid.asString(),
                webUrl != null ? webUrl : trimTrailingSlash(config.baseUrl()),
                truncate(text(node, "title"), MAX_TITLE_LENGTH),
                mapState(text(node, "state")));
    }

    /** GitLab uses "opened"/"closed"; anything else (or absent) is treated as unknown. */
    private static IssueState mapState(String state) {
        if (state == null) {
            return IssueState.UNKNOWN;
        }
        return switch (state.toLowerCase()) {
            case "opened", "open", "reopened" -> IssueState.OPEN;
            case "closed", "locked" -> IssueState.CLOSED;
            default -> IssueState.UNKNOWN;
        };
    }
}
