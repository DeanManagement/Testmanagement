package com.deanmanagement.testmanagement.project.internal.issuetracker;

import com.deanmanagement.testmanagement.project.internal.entity.IssueState;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Azure Boards adapter over the work item tracking REST API (PRD-026 §3.3), for Azure DevOps
 * Services and Server. The base URL includes the organization or collection; the project reference
 * is the Azure DevOps project. Work item ids are unique in the organization, so the external id is
 * the bare number.
 *
 * <p>Whether a work item is open is read from its state's <em>category</em>, never its name:
 * processes name their states differently and customers rename them, but every state belongs to
 * one of five categories.
 */
@Component
public class AzureDevOpsIssueProvider extends HttpIssueProviderSupport implements IssueTrackerProvider {

    static final String DEFAULT_API_VERSION = "7.1";
    static final String DEFAULT_WORK_ITEM_TYPE = "Bug";
    static final String REPRO_STEPS = "Microsoft.VSTS.TCM.ReproSteps";
    static final String DESCRIPTION = "System.Description";
    private static final String FIELDS = "System.Id,System.Title,System.State,System.WorkItemType,System.TeamProject";
    private static final int SEARCH_LIMIT = 20;
    private static final int MAX_TITLE_LENGTH = 500;
    private static final int HTTP_NON_AUTHORITATIVE = 203;
    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_NOT_FOUND = 404;
    /** State categories rarely change; one lookup per type and config serves a whole refresh pass. */
    private static final Duration STATE_CACHE_TTL = Duration.ofMinutes(10);

    private final Map<String, CachedStates> stateCache = new ConcurrentHashMap<>();

    public AzureDevOpsIssueProvider(IssueTrackerProperties properties, ObjectMapper objectMapper) {
        super(properties, objectMapper);
    }

    @Override
    public IssueTrackerProviderType type() {
        return IssueTrackerProviderType.AZURE_DEVOPS;
    }

    @Override
    protected String providerName() {
        return "Azure DevOps";
    }

    @Override
    protected HttpRequest.Builder authenticate(HttpRequest.Builder builder, String token) {
        String basic = Base64.getEncoder().encodeToString((":" + token).getBytes(StandardCharsets.UTF_8));
        return builder.header("Authorization", "Basic " + basic);
    }

    /** Two calls: WIQL returns ids only, and the work items are then read in one batch. */
    @Override
    public List<Issue> search(DecryptedConfig config, String query) {
        JsonNode found = postJson(config, projectApi(config) + "/_apis/wit/wiql?$top=" + SEARCH_LIMIT
                + version(config, "&"), Map.of("query", Wiql.search(workItemType(config), query)));
        List<String> ids = new ArrayList<>();
        found.path("workItems").forEach(item -> {
            String id = text(item, "id");
            if (id != null && ids.size() < SEARCH_LIMIT) {
                ids.add(id);
            }
        });
        if (ids.isEmpty()) {
            return List.of(); // A batch read with no ids is a 400.
        }
        JsonNode items = getJson(config, base(config) + "/_apis/wit/workitems?ids=" + String.join(",", ids)
                + "&fields=" + FIELDS + version(config, "&"));
        List<Issue> issues = new ArrayList<>();
        items.path("value").forEach(item -> issues.add(toIssue(config, item)));
        return issues;
    }

    /**
     * Files a work item of the configured type. Its text goes to Repro Steps, which is where a Bug
     * shows it in every standard process; a type without that field is retried once with
     * Description. The body is HTML, so the Markdown draft is escaped and its line breaks kept.
     */
    @Override
    public Issue create(DecryptedConfig config, IssueDraft draft) {
        String url = projectApi(config) + "/_apis/wit/workitems/$" + encodePath(workItemType(config))
                + version(config, "?");
        HttpResponse<String> response = exchange(patch(config, url, draft, REPRO_STEPS), config);
        if (response.statusCode() == HTTP_BAD_REQUEST && response.body() != null
                && response.body().contains(REPRO_STEPS)) {
            response = exchange(patch(config, url, draft, DESCRIPTION), config);
        }
        if (response.statusCode() == HTTP_BAD_REQUEST || response.statusCode() == HTTP_NOT_FOUND) {
            // The project was checked when the tracker was saved; what is left is the type.
            throw new UpstreamServiceException("Azure DevOps could not create a work item of type '"
                    + workItemType(config) + "'; check the work item type in the tracker settings");
        }
        return toIssue(config, parse(response, config));
    }

    @Override
    public Issue get(DecryptedConfig config, String externalId) {
        String id = issueNumber(externalId);
        return toIssue(config, getJson(config, base(config) + "/_apis/wit/workitems/" + id + "?fields=" + FIELDS
                + version(config, "&")));
    }

    /** Reads the project itself: proves the organization, the project and the token in one call. */
    @Override
    public void testConnection(DecryptedConfig config) {
        getJson(config, base(config) + "/_apis/projects/" + encodePath(config.projectRef()) + version(config, "?"));
    }

    /**
     * A rejected PAT is answered with 203 and a sign-in page, not 401. A 400 about the api-version
     * means an older Server: the message says which setting fixes it.
     */
    @Override
    protected void rejectFailure(HttpResponse<String> response, DecryptedConfig config) {
        if (response.statusCode() == HTTP_NON_AUTHORITATIVE) {
            throw new UpstreamServiceException("Azure DevOps rejected the configured access token (HTTP 203)");
        }
        if (response.statusCode() == HTTP_BAD_REQUEST && response.body() != null
                && response.body().toLowerCase(Locale.ROOT).contains("api-version")) {
            throw new UpstreamServiceException("Azure DevOps does not support API version " + apiVersion(config)
                    + "; set the API version in the tracker settings (6.0 for Server 2020, 5.0 for Server 2019)");
        }
        super.rejectFailure(response, config);
    }

    // ---- helpers --------------------------------------------------------------------------------

    private HttpRequest patch(DecryptedConfig config, String url, IssueDraft draft, String bodyField) {
        List<Map<String, Object>> operations = List.of(
                Map.of("op", "add", "path", "/fields/System.Title", "value", truncate(draft.title(), MAX_TITLE_LENGTH)),
                Map.of("op", "add", "path", "/fields/" + bodyField, "value", toHtml(draft.body())));
        return request(config, url)
                .header("Content-Type", "application/json-patch+json")
                .POST(HttpRequest.BodyPublishers.ofString(serializeList(operations), StandardCharsets.UTF_8))
                .build();
    }

    /** Escaped text with line breaks, deliberately not a Markdown renderer (PRD-026 §3.3). */
    static String toHtml(String markdown) {
        if (markdown == null) {
            return "";
        }
        return markdown.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;")
                .replace("\r\n", "\n").replace("\n", "<br>");
    }

    private Issue toIssue(DecryptedConfig config, JsonNode item) {
        String id = text(item, "id");
        if (id == null) {
            throw new UpstreamServiceException("Azure DevOps work item response is missing an id");
        }
        JsonNode fields = item.path("fields");
        String project = firstNonNull(text(fields, "System.TeamProject"), config.projectRef());
        return new Issue(id, projectUrl(config, project) + "/_workitems/edit/" + id,
                truncate(text(fields, "System.Title"), MAX_TITLE_LENGTH),
                stateOf(config, project, text(fields, "System.WorkItemType"), text(fields, "System.State")));
    }

    private IssueState stateOf(DecryptedConfig config, String project, String type, String state) {
        if (type == null || state == null) {
            return IssueState.UNKNOWN;
        }
        return StateCategories.toIssueState(categories(config, project, type).get(state));
    }

    /** State name → category for one work item type, from {@code workitemtypes/{type}/states}. */
    private Map<String, String> categories(DecryptedConfig config, String project, String type) {
        String key = config.config().getId() + "|" + project + "|" + type;
        CachedStates cached = stateCache.get(key);
        if (cached != null && cached.fetchedAt().plus(STATE_CACHE_TTL).isAfter(Instant.now())) {
            return cached.categories();
        }
        JsonNode states = getJson(config, projectUrl(config, project) + "/_apis/wit/workitemtypes/"
                + encodePath(type) + "/states" + version(config, "?"));
        Map<String, String> categories = new HashMap<>();
        states.path("value").forEach(state -> {
            String name = text(state, "name");
            if (name != null) {
                categories.put(name, text(state, "category"));
            }
        });
        stateCache.put(key, new CachedStates(categories, Instant.now()));
        return categories;
    }

    private String serializeList(List<Map<String, Object>> operations) {
        try {
            return objectMapper.writeValueAsString(operations);
        } catch (Exception e) {
            throw new UpstreamServiceException("Could not build the Azure DevOps request body");
        }
    }

    private String projectApi(DecryptedConfig config) {
        return projectUrl(config, config.projectRef());
    }

    private String projectUrl(DecryptedConfig config, String project) {
        return base(config) + "/" + encodePath(project);
    }

    private static String base(DecryptedConfig config) {
        return trimTrailingSlash(config.baseUrl());
    }

    private static String workItemType(DecryptedConfig config) {
        String configured = config.config().getWorkItemType();
        return configured == null || configured.isBlank() ? DEFAULT_WORK_ITEM_TYPE : configured;
    }

    private static String apiVersion(DecryptedConfig config) {
        String configured = config.config().getApiVersion();
        return configured == null || configured.isBlank() ? DEFAULT_API_VERSION : configured;
    }

    private static String version(DecryptedConfig config, String separator) {
        return separator + "api-version=" + encode(apiVersion(config));
    }

    private static String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }

    private record CachedStates(Map<String, String> categories, Instant fetchedAt) {
    }

    /** The five state categories Azure DevOps has, whatever the states are called. */
    static final class StateCategories {

        private StateCategories() {
        }

        static IssueState toIssueState(String category) {
            if (category == null) {
                return IssueState.UNKNOWN;
            }
            return switch (category) {
                case "Completed", "Removed" -> IssueState.CLOSED;
                case "Proposed", "InProgress", "Resolved" -> IssueState.OPEN;
                default -> IssueState.UNKNOWN;
            };
        }
    }
}
