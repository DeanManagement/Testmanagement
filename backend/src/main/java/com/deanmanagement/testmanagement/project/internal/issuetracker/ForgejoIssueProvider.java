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
 * Forgejo adapter over the v1 REST API (PRD-010). Also works against Gitea, which Forgejo forked
 * from and stays API-compatible with, and against Codeberg, which runs Forgejo.
 *
 * <p>Two things differ from GitLab and drive the shape of this class. Repositories are addressed as
 * two separate path segments, {@code /repos/{owner}/{repo}}, rather than one URL-encoded path — so
 * the configured {@code owner/repo} is split and each half encoded on its own. And the issues
 * endpoint returns pull requests alongside issues unless {@code type=issues} is passed, which would
 * otherwise let a tester "link" a merge request as though it were a defect.
 */
@Component
public class ForgejoIssueProvider extends HttpIssueProviderSupport implements IssueTrackerProvider {

    /** Forgejo caps page size at 50; 20 is plenty for a typeahead. */
    private static final int SEARCH_PAGE_SIZE = 20;
    private static final int MAX_TITLE_LENGTH = 500;

    public ForgejoIssueProvider(IssueTrackerProperties properties, ObjectMapper objectMapper) {
        super(properties, objectMapper);
    }

    @Override
    public IssueTrackerProviderType type() {
        return IssueTrackerProviderType.FORGEJO;
    }

    @Override
    protected String providerName() {
        return "Forgejo";
    }

    @Override
    protected HttpRequest.Builder authenticate(HttpRequest.Builder builder, String token) {
        // Forgejo accepts both "token <t>" and "Bearer <t>"; "token" also works on older Gitea.
        return builder.header("Authorization", "token " + token);
    }

    @Override
    public List<Issue> search(DecryptedConfig config, String query) {
        String url = repoApi(config) + "/issues?type=issues&state=all&limit=" + SEARCH_PAGE_SIZE
                + "&q=" + encode(query);
        JsonNode body = getJson(config, url);
        if (!body.isArray()) {
            throw new UpstreamServiceException("Forgejo returned an unexpected search response");
        }
        List<Issue> issues = new ArrayList<>();
        for (JsonNode node : body) {
            // Belt and braces: type=issues should already exclude these, but older Gitea builds
            // have shipped bugs here and a pull request must never be linked as a defect.
            if (node.get("pull_request") != null && !node.get("pull_request").isNull()) {
                continue;
            }
            issues.add(toIssue(config, node));
        }
        return issues;
    }

    @Override
    public Issue create(DecryptedConfig config, IssueDraft draft) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", draft.title());
        payload.put("body", draft.body());
        return toIssue(config, postJson(config, repoApi(config) + "/issues", payload));
    }

    @Override
    public Issue get(DecryptedConfig config, String externalId) {
        String index = issueNumber(externalId);
        return toIssue(config, getJson(config, repoApi(config) + "/issues/" + encodePath(index)));
    }

    @Override
    public void testConnection(DecryptedConfig config) {
        // The repo endpoint proves the token is valid *and* can see this repository; hitting
        // /user would only prove the former.
        getJson(config, repoApi(config));
    }

    /**
     * {@code {base}/api/v1/repos/{owner}/{repo}}. The owner and repo are encoded separately —
     * encoding the pair as one value would turn the slash into %2F and produce a 404.
     */
    private String repoApi(DecryptedConfig config) {
        String ref = config.projectRef().trim();
        int slash = ref.indexOf('/');
        if (slash <= 0 || slash == ref.length() - 1 || ref.indexOf('/', slash + 1) >= 0) {
            throw new IllegalArgumentException(
                    "Forgejo project reference must be in the form owner/repository, got: " + ref);
        }
        String owner = ref.substring(0, slash);
        String repo = ref.substring(slash + 1);
        return trimTrailingSlash(config.baseUrl()) + "/api/v1/repos/" + encodePath(owner) + "/" + encodePath(repo);
    }

    private Issue toIssue(DecryptedConfig config, JsonNode node) {
        JsonNode number = node.get("number");
        if (number == null || number.isNull()) {
            throw new UpstreamServiceException("Forgejo issue response is missing a number");
        }
        String htmlUrl = text(node, "html_url");
        return new Issue(
                config.projectRef() + "#" + number.asString(),
                htmlUrl != null ? htmlUrl : trimTrailingSlash(config.baseUrl()),
                truncate(text(node, "title"), MAX_TITLE_LENGTH),
                mapState(text(node, "state")));
    }

    /** Forgejo uses "open"/"closed"; anything else (or absent) is treated as unknown. */
    private static IssueState mapState(String state) {
        if (state == null) {
            return IssueState.UNKNOWN;
        }
        return switch (state.toLowerCase()) {
            case "open", "opened", "reopened" -> IssueState.OPEN;
            case "closed" -> IssueState.CLOSED;
            default -> IssueState.UNKNOWN;
        };
    }
}
