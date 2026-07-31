package com.deanmanagement.testmanagement.project.internal.issuetracker;

import com.deanmanagement.testmanagement.project.internal.entity.IssueState;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
@RequiredArgsConstructor
public class GitLabIssueProvider implements IssueTrackerProvider {

    private static final int SEARCH_PAGE_SIZE = 20;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final IssueTrackerProperties properties;
    private final ObjectMapper objectMapper;

    private volatile HttpClient httpClient;

    @Override
    public IssueTrackerProviderType type() {
        return IssueTrackerProviderType.GITLAB;
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

        HttpRequest request = baseRequest(config, projectApi(config) + "/issues")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(serialize(payload), StandardCharsets.UTF_8))
                .build();

        return toIssue(config, send(request, config));
    }

    @Override
    public Issue get(DecryptedConfig config, String externalId) {
        String iid = internalId(externalId);
        return toIssue(config, getJson(config, projectApi(config) + "/issues/" + encode(iid)));
    }

    @Override
    public void testConnection(DecryptedConfig config) {
        // Fetching the project itself proves both that the token is valid and that it can see the
        // configured project — a token that authenticates but lacks access would otherwise only
        // fail later, at the first search.
        getJson(config, projectApi(config));
    }

    // ---- HTTP -------------------------------------------------------------

    private HttpClient client() {
        HttpClient local = httpClient;
        if (local == null) {
            synchronized (this) {
                local = httpClient;
                if (local == null) {
                    local = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                            // A redirect would re-send the PRIVATE-TOKEN header to whatever host the
                            // tracker names, so redirects are never followed.
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build();
                    httpClient = local;
                }
            }
        }
        return local;
    }

    private HttpRequest.Builder baseRequest(DecryptedConfig config, String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.readTimeoutMs()))
                .header("PRIVATE-TOKEN", config.token())
                .header("Accept", "application/json");
    }

    private JsonNode getJson(DecryptedConfig config, String url) {
        return send(baseRequest(config, url).GET().build(), config);
    }

    private JsonNode send(HttpRequest request, DecryptedConfig config) {
        HttpResponse<String> response;
        try {
            response = client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamServiceException("Interrupted while calling GitLab");
        } catch (Exception e) {
            // The message may contain the host but never the token, which lives only in a header.
            throw new UpstreamServiceException("Could not reach GitLab at " + config.baseUrl(), e);
        }

        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new UpstreamServiceException(
                    "GitLab rejected the configured access token (HTTP " + status + ")");
        }
        if (status == 404) {
            throw new UpstreamServiceException(
                    "GitLab project '" + config.projectRef() + "' was not found, or the token cannot see it");
        }
        if (status == 429) {
            throw new UpstreamServiceException("GitLab rate limit reached; try again shortly");
        }
        if (status < 200 || status >= 300) {
            throw new UpstreamServiceException("GitLab returned HTTP " + status);
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new UpstreamServiceException("GitLab returned an empty response");
        }
        if (body.length() > MAX_RESPONSE_BYTES) {
            throw new UpstreamServiceException("GitLab response was too large to process");
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new UpstreamServiceException("GitLab returned a malformed response");
        }
    }

    // ---- Mapping ----------------------------------------------------------

    private String projectApi(DecryptedConfig config) {
        // GitLab addresses projects by URL-encoded path, so "group/sub/project" becomes
        // "group%2Fsub%2Fproject"; a numeric id passes through unchanged.
        return trimTrailingSlash(config.baseUrl()) + "/api/v4/projects/" + encode(config.projectRef());
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
                text(node, "title"),
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

    /** Strips the {@code projectRef#} prefix the tool stores, leaving GitLab's per-project iid. */
    private static String internalId(String externalId) {
        int hash = externalId.lastIndexOf('#');
        String iid = hash >= 0 ? externalId.substring(hash + 1) : externalId;
        if (iid.isBlank() || !iid.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Not a valid GitLab issue reference: " + externalId);
        }
        return iid;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new UpstreamServiceException("Could not build the GitLab request body");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
