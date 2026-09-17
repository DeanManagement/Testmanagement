package com.deanmanagement.testmanagement.project.internal.issuetracker;

import com.deanmanagement.testmanagement.project.internal.entity.IssueState;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GitHub Issues adapter (PRD-029 §3.3), for github.com and GitHub Enterprise Server 3.x+.
 *
 * <p>Close to {@link ForgejoIssueProvider} — same {@code owner/repo} addressing, same
 * {@code owner/repo#123} ids, same endpoint that hands back pull requests alongside issues. What
 * differs: github.com serves its API from another host, GitHub insists on a {@code User-Agent}, and
 * it signals a rate limit with 403, which the shared mapping would report as a rejected token.
 */
@Component
public class GitHubIssueProvider extends HttpIssueProviderSupport implements IssueTrackerProvider {

    private static final int SEARCH_PAGE_SIZE = 20;
    private static final int MAX_TITLE_LENGTH = 500;
    private static final String API_VERSION = "2022-11-28";
    private static final String USER_AGENT = "Testmanagement";
    private static final int GONE = 410;

    public GitHubIssueProvider(IssueTrackerProperties properties, ObjectMapper objectMapper) {
        super(properties, objectMapper);
    }

    @Override
    public IssueTrackerProviderType type() {
        return IssueTrackerProviderType.GITHUB;
    }

    @Override
    protected String providerName() {
        return "GitHub";
    }

    @Override
    protected HttpRequest.Builder authenticate(HttpRequest.Builder builder, String token) {
        return builder
                .header("Authorization", "Bearer " + token)
                // Replaces the shared "application/json": GitHub's own media type pins the format.
                .setHeader("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                // GitHub rejects requests without one, and the JDK client's default gets blocked by
                // CDNs in front of some installations (the lesson from PRD-028 §8).
                .header("User-Agent", USER_AGENT);
    }

    @Override
    public List<Issue> search(DecryptedConfig config, String query) {
        String trimmed = query.trim();
        String number = trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
        if (!number.isEmpty() && number.chars().allMatch(Character::isDigit)) {
            // A number needs no search — and the search API allows only 30 requests a minute.
            return getJsonIfFound(config, issueUrl(config, number))
                    .filter(node -> !isPullRequest(node))
                    .map(node -> List.of(toIssue(config, node)))
                    .orElse(List.of());
        }

        // is:issue keeps pull requests out server-side; repo: keeps other repositories out.
        String q = "repo:" + ownerAndRepo(config) + " is:issue " + trimmed;
        JsonNode body = getJson(config, apiBase(config.baseUrl()) + "/search/issues?per_page="
                + SEARCH_PAGE_SIZE + "&q=" + encode(q));
        JsonNode items = body.get("items");
        if (items == null || !items.isArray()) {
            throw new UpstreamServiceException("GitHub returned an unexpected search response");
        }
        List<Issue> issues = new ArrayList<>();
        for (JsonNode node : items) {
            if (!isPullRequest(node)) {
                issues.add(toIssue(config, node));
            }
        }
        return issues;
    }

    @Override
    public Issue create(DecryptedConfig config, IssueDraft draft) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", draft.title());
        // Markdown as built by IssueLinkService; GitHub renders it natively.
        payload.put("body", draft.body());
        return toIssue(config, postJson(config, repoApi(config) + "/issues", payload));
    }

    @Override
    public Issue get(DecryptedConfig config, String externalId) {
        String number = issueNumber(externalId);
        JsonNode node = getJson(config, issueUrl(config, number));
        // GET /issues/{n} answers for pull requests as well, and one must never be linked as a defect.
        if (isPullRequest(node)) {
            throw new UpstreamServiceException("#" + number + " is a pull request, not an issue");
        }
        return toIssue(config, node);
    }

    @Override
    public void testConnection(DecryptedConfig config) {
        JsonNode repository = getJson(config, repoApi(config));
        JsonNode hasIssues = repository.get("has_issues");
        // Common on forks. Caught here so it does not first show up as a 410 when someone files.
        if (hasIssues != null && hasIssues.isBoolean() && !hasIssues.asBoolean()) {
            throw new UpstreamServiceException("GitHub repository '" + config.projectRef()
                    + "' has issues disabled; enable them in the repository settings");
        }
    }

    /**
     * GitHub answers a rate limit — notably the <em>secondary</em> one on the search API — with 403
     * plus {@code retry-after} or {@code x-ratelimit-remaining: 0}. The shared mapping would call
     * that a rejected token and send an admin off to rotate a perfectly good one.
     */
    @Override
    protected void rejectFailure(HttpResponse<String> response, DecryptedConfig config) {
        int status = response.statusCode();
        if (status == 403 && isRateLimited(response)) {
            throw new UpstreamServiceException("GitHub rate limit reached; try again shortly");
        }
        if (status == GONE) {
            throw new UpstreamServiceException("GitHub repository '" + config.projectRef()
                    + "' has issues disabled; enable them in the repository settings");
        }
        super.rejectFailure(response, config);
    }

    private static boolean isRateLimited(HttpResponse<String> response) {
        return response.headers().firstValue("retry-after").isPresent()
                || response.headers().firstValue("x-ratelimit-remaining").filter("0"::equals).isPresent();
    }

    /**
     * Where the REST API lives for a base URL as an admin would paste it: github.com is served from
     * {@code api.github.com}, any other host is an Enterprise Server with the API under
     * {@code /api/v3}. The stored base URL stays as typed.
     */
    static String apiBase(String baseUrl) {
        String base = trimTrailingSlash(baseUrl.trim());
        String host = URI.create(base).getHost();
        if (host != null && (host.equalsIgnoreCase("github.com") || host.equalsIgnoreCase("www.github.com"))) {
            return "https://api.github.com";
        }
        return base + "/api/v3";
    }

    private String repoApi(DecryptedConfig config) {
        String[] parts = ownerAndRepo(config).split("/");
        return apiBase(config.baseUrl()) + "/repos/" + encodePath(parts[0]) + "/" + encodePath(parts[1]);
    }

    private String issueUrl(DecryptedConfig config, String number) {
        return repoApi(config) + "/issues/" + encodePath(number);
    }

    private static String ownerAndRepo(DecryptedConfig config) {
        String ref = config.projectRef().trim();
        int slash = ref.indexOf('/');
        if (slash <= 0 || slash == ref.length() - 1 || ref.indexOf('/', slash + 1) >= 0) {
            throw new IllegalArgumentException(
                    "GitHub project reference must be in the form owner/repository, got: " + ref);
        }
        return ref;
    }

    private static boolean isPullRequest(JsonNode node) {
        JsonNode pullRequest = node.get("pull_request");
        return pullRequest != null && !pullRequest.isNull();
    }

    private Issue toIssue(DecryptedConfig config, JsonNode node) {
        JsonNode number = node.get("number");
        if (number == null || number.isNull()) {
            throw new UpstreamServiceException("GitHub issue response is missing a number");
        }
        String htmlUrl = text(node, "html_url");
        return new Issue(
                config.projectRef().trim() + "#" + number.asString(),
                htmlUrl != null ? htmlUrl : trimTrailingSlash(config.baseUrl()),
                truncate(text(node, "title"), MAX_TITLE_LENGTH),
                mapState(text(node, "state")));
    }

    /** {@code closed} covers every {@code state_reason}, "not planned" included. */
    private static IssueState mapState(String state) {
        if ("open".equalsIgnoreCase(state)) {
            return IssueState.OPEN;
        }
        return "closed".equalsIgnoreCase(state) ? IssueState.CLOSED : IssueState.UNKNOWN;
    }
}
