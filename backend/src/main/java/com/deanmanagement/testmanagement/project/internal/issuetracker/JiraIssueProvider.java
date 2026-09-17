package com.deanmanagement.testmanagement.project.internal.issuetracker;

import com.deanmanagement.testmanagement.project.internal.entity.IssueState;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Jira adapter (PRD-029 §3.2) for Jira Cloud and Jira Data Center / Server 9.x+.
 *
 * <p>The two flavours differ in exactly two places — how they authenticate and where they search —
 * and are told apart by the host alone: {@code *.atlassian.net} is Cloud. There is no setting for
 * it, so it cannot drift from the truth.
 *
 * <p>Issues are created through REST v2 on both. v2 still accepts wiki markup for the description
 * on Cloud, which spares building Atlassian Document Format for a body that is a few bold labels.
 */
@Component
public class JiraIssueProvider extends HttpIssueProviderSupport implements IssueTrackerProvider {

    static final String DEFAULT_ISSUE_TYPE = "Bug";
    private static final String CLOUD_HOST_SUFFIX = ".atlassian.net";
    private static final int SEARCH_PAGE_SIZE = 20;
    private static final int MAX_TITLE_LENGTH = 500;
    private static final Pattern ISSUE_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_]*-\\d+");

    private final Predicate<String> cloudDetector;

    @Autowired
    public JiraIssueProvider(IssueTrackerProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, JiraIssueProvider::isAtlassianCloud);
    }

    /** For tests: a stub on 127.0.0.1 can never look like Cloud, so they say which flavour it plays. */
    JiraIssueProvider(IssueTrackerProperties properties, ObjectMapper objectMapper,
                      Predicate<String> cloudDetector) {
        super(properties, objectMapper);
        this.cloudDetector = cloudDetector;
    }

    /** Exact suffix on the parsed host, so {@code atlassian.net.evil.example} is never sent Basic credentials. */
    public static boolean isAtlassianCloud(String baseUrl) {
        String host = URI.create(baseUrl.trim()).getHost();
        return host != null && host.toLowerCase().endsWith(CLOUD_HOST_SUFFIX);
    }

    @Override
    public IssueTrackerProviderType type() {
        return IssueTrackerProviderType.JIRA;
    }

    @Override
    protected String providerName() {
        return "Jira";
    }

    /** Data Center: a personal access token as a bearer (8.14+). Cloud replaces it in {@link #request}. */
    @Override
    protected HttpRequest.Builder authenticate(HttpRequest.Builder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    @Override
    protected HttpRequest.Builder request(DecryptedConfig config, String url) {
        HttpRequest.Builder builder = super.request(config, url);
        if (!isCloud(config)) {
            return builder;
        }
        String username = config.authUsername();
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Jira Cloud needs the account email that the API token belongs to");
        }
        String credentials = username.trim() + ":" + config.token();
        return builder.setHeader("Authorization",
                "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public List<Issue> search(DecryptedConfig config, String query) {
        String trimmed = query.trim();
        if (ISSUE_KEY.matcher(trimmed).matches()) {
            // Looked up rather than searched for: JQL that names a key which does not exist is a
            // 400 in Jira, so "key = X OR text ~ X" would fail for every near-miss.
            var byKey = getJsonIfFound(config, issueUrl(config, trimmed.toUpperCase()));
            if (byKey.isPresent()) {
                return List.of(toIssue(config, byKey.get()));
            }
        }

        String jql = "project = \"" + escapeJql(ProjectRef.of(config).key()) + "\" AND text ~ \""
                + escapeJql(trimmed) + "\"";
        // The legacy /search was removed from Cloud in 2025; Data Center only has the legacy one.
        String endpoint = isCloud(config) ? "/rest/api/3/search/jql" : "/rest/api/2/search";
        JsonNode body = getJson(config, api(config, endpoint) + "?jql=" + encode(jql)
                + "&fields=summary,status&maxResults=" + SEARCH_PAGE_SIZE);
        JsonNode issues = body.get("issues");
        if (issues == null || !issues.isArray()) {
            throw new UpstreamServiceException("Jira returned an unexpected search response");
        }
        List<Issue> found = new ArrayList<>();
        for (JsonNode node : issues) {
            found.add(toIssue(config, node));
        }
        return found;
    }

    @Override
    public Issue create(DecryptedConfig config, IssueDraft draft) {
        ProjectRef ref = ProjectRef.of(config);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("project", Map.of("key", ref.key()));
        fields.put("summary", draft.title());
        fields.put("description", MarkdownToJiraWiki.convert(draft.body()));
        fields.put("issuetype", Map.of("name", ref.issueType()));

        JsonNode created = postJson(config, api(config, "/rest/api/2/issue"), Map.of("fields", fields));
        String key = text(created, "key");
        if (key == null) {
            throw new UpstreamServiceException("Jira issue response is missing a key");
        }
        // The response carries only id/key/self. A new issue is open and has the title just sent,
        // so the Issue is assembled here instead of spending a second request to read it back.
        return new Issue(key, browseUrl(config, key), truncate(draft.title(), MAX_TITLE_LENGTH), IssueState.OPEN);
    }

    @Override
    public Issue get(DecryptedConfig config, String externalId) {
        return toIssue(config, getJson(config, issueUrl(config, externalId.trim())));
    }

    @Override
    public void testConnection(DecryptedConfig config) {
        ProjectRef ref = ProjectRef.of(config);
        JsonNode project = getJson(config, api(config, "/rest/api/2/project/" + encodePath(ref.key())));

        List<String> available = new ArrayList<>();
        JsonNode issueTypes = project.get("issueTypes");
        if (issueTypes != null && issueTypes.isArray()) {
            issueTypes.forEach(type -> available.add(text(type, "name")));
        }
        // Checked now so that "there is no Bug in this project" does not first appear when a tester
        // files from a failed result.
        if (available.stream().noneMatch(ref.issueType()::equalsIgnoreCase)) {
            throw new UpstreamServiceException("Jira project '" + ref.key() + "' has no issue type '"
                    + ref.issueType() + "'. It offers: " + String.join(", ", available)
                    + ". Put the one to use after the key, e.g. " + ref.key() + ":Task");
        }
    }

    /**
     * A 400 on create is nearly always a create screen that requires fields this tool does not
     * send. Jira names them, so the message does too, instead of "returned HTTP 400".
     */
    @Override
    protected void rejectFailure(HttpResponse<String> response, DecryptedConfig config) {
        if (response.statusCode() == 400) {
            List<String> required = requiredFields(response.body());
            if (!required.isEmpty()) {
                throw new UpstreamServiceException("Jira requires: " + String.join(", ", required)
                        + ". This tool cannot fill those in — link an issue created in Jira instead");
            }
        }
        super.rejectFailure(response, config);
    }

    private List<String> requiredFields(String body) {
        List<String> names = new ArrayList<>();
        try {
            JsonNode errors = objectMapper.readTree(body).get("errors");
            if (errors != null && errors.isObject()) {
                errors.propertyNames().forEach(names::add);
            }
        } catch (Exception notJson) {
            // Fall through to the generic status message; the body is not echoed either way.
        }
        return names;
    }

    private boolean isCloud(DecryptedConfig config) {
        return cloudDetector.test(config.baseUrl());
    }

    private static String api(DecryptedConfig config, String path) {
        return trimTrailingSlash(config.baseUrl().trim()) + path;
    }

    private static String issueUrl(DecryptedConfig config, String key) {
        return api(config, "/rest/api/2/issue/" + encodePath(key)) + "?fields=summary,status";
    }

    private static String browseUrl(DecryptedConfig config, String key) {
        return api(config, "/browse/" + encodePath(key));
    }

    /** Quotes and backslashes are the only characters that can end a JQL string early. */
    private static String escapeJql(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Issue toIssue(DecryptedConfig config, JsonNode node) {
        // The key Jira returns, not the one asked for: an issue moved to another project answers
        // under its new key, and the link should follow it.
        String key = text(node, "key");
        if (key == null) {
            throw new UpstreamServiceException("Jira issue response is missing a key");
        }
        JsonNode fields = node.path("fields");
        return new Issue(key, browseUrl(config, key),
                truncate(text(fields, "summary"), MAX_TITLE_LENGTH),
                mapState(fields.path("status").path("statusCategory").path("key").asString(null)));
    }

    /**
     * By status <em>category</em>, which Jira fixes to new / indeterminate / done. Status
     * <em>names</em> belong to each workflow ("In Arbeit", "Ready for QA") and must not be matched.
     */
    private static IssueState mapState(String statusCategoryKey) {
        if (statusCategoryKey == null) {
            return IssueState.UNKNOWN;
        }
        return switch (statusCategoryKey) {
            case "new", "indeterminate" -> IssueState.OPEN;
            case "done" -> IssueState.CLOSED;
            default -> IssueState.UNKNOWN;
        };
    }

    /** {@code PROJ}, or {@code PROJ:Task} to file something other than a Bug. */
    private record ProjectRef(String key, String issueType) {

        static ProjectRef of(DecryptedConfig config) {
            String ref = config.projectRef().trim();
            int colon = ref.indexOf(':');
            String key = (colon < 0 ? ref : ref.substring(0, colon)).trim();
            String issueType = colon < 0 ? "" : ref.substring(colon + 1).trim();
            if (key.isEmpty()) {
                throw new IllegalArgumentException(
                        "Jira project reference must start with the project key, e.g. PROJ or PROJ:Task, got: " + ref);
            }
            return new ProjectRef(key, issueType.isEmpty() ? DEFAULT_ISSUE_TYPE : issueType);
        }
    }
}
