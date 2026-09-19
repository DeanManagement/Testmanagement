package com.deanmanagement.testmanagement.project.internal.buildserver;

import com.deanmanagement.testmanagement.project.internal.ci.CiResult;
import com.deanmanagement.testmanagement.project.internal.entity.BuildServerProviderType;
import com.deanmanagement.testmanagement.project.internal.entity.PipelineRunStatus;
import com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Azure Pipelines adapter over the {@code pipelines} REST API (PRD-026 §3.2), for Azure DevOps
 * Services and Server alike. The base URL includes the organization or collection
 * ({@code https://dev.azure.com/contoso}); a workflow's {@code repoRef} is the Azure DevOps project
 * and its {@code workflowRef} the numeric pipeline id.
 *
 * <p>Authentication is a PAT as Basic auth with an empty user name. A rejected PAT is answered with
 * 203 and a sign-in page rather than 401, which {@link HttpBuildProviderSupport} reports as a
 * rejected token.
 */
@Component
public class AzureDevOpsProvider extends HttpBuildProviderSupport implements BuildServerProvider {

    static final String DEFAULT_API_VERSION = "7.1";
    /** Variables the tool injects itself; everything else is a template parameter of the YAML. */
    private static final String CORRELATION_PREFIX = "TM_";
    private static final int DISCOVER_LIMIT = 1000;
    private static final int RESULT_PAGE_SIZE = 200;
    private static final int HTTP_BAD_REQUEST = 400;

    public AzureDevOpsProvider(BuildServerProperties properties, ObjectMapper objectMapper) {
        super(properties, objectMapper);
    }

    @Override
    public BuildServerProviderType type() {
        return BuildServerProviderType.AZURE_DEVOPS;
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

    /**
     * Template parameters must be declared in the YAML and queue-time variables must be settable,
     * or Azure DevOps answers 400; the trigger is then retried once without either. That loses only
     * the report-back correlation, and pulling results does not need it.
     */
    @Override
    public TriggerResult trigger(DecryptedConfig config, TriggerSpec spec) {
        String url = pipelineApi(config, spec.repoRef(), spec.workflowRef()) + "/runs" + version(config, "?");
        HttpResponse<String> response = sendTolerating(post(config, url, runPayload(spec, true)), config,
                HTTP_BAD_REQUEST);
        if (response.statusCode() == HTTP_BAD_REQUEST) {
            // The retry is the last word: a second 400 is an error like any other.
            response = send(post(config, url, runPayload(spec, false)), config);
        }
        JsonNode run = parseBody(response);
        String id = requireId(run);
        return new TriggerResult(mapStatus(text(run, "state"), text(run, "result")), id,
                webUrl(config, spec.repoRef(), run, id));
    }

    @Override
    public StatusResult fetchStatus(DecryptedConfig config, StatusQuery query) {
        if (query.externalRunId() == null) {
            // The trigger always returns the run id, so a null here means the trigger itself failed.
            throw new UpstreamServiceException("Azure DevOps run id is missing; cannot poll status");
        }
        JsonNode run = getJson(config, pipelineApi(config, query.repoRef(), query.workflowRef())
                + "/runs/" + encodePath(query.externalRunId()) + version(config, "?"));
        return new StatusResult(mapStatus(text(run, "state"), text(run, "result")), null,
                webUrl(config, query.repoRef(), run, query.externalRunId()));
    }

    /**
     * A build-server connection is global, so no Azure DevOps project is known here: listing one
     * project proves the organization and the token, and the project is checked per workflow.
     */
    @Override
    public void testConnection(DecryptedConfig config) {
        getJson(config, base(config) + "/_apis/projects?$top=1" + version(config, "&"));
    }

    @Override
    public List<DiscoveredWorkflow> discover(DecryptedConfig config, String repoRef) {
        JsonNode body = getJson(config, projectApi(config, repoRef) + "/_apis/pipelines?$top=" + DISCOVER_LIMIT
                + version(config, "&"));
        List<DiscoveredWorkflow> discovered = new ArrayList<>();
        for (JsonNode pipeline : values(body)) {
            String id = text(pipeline, "id");
            if (id != null) {
                discovered.add(new DiscoveredWorkflow(displayName(pipeline), repoRef, id, null));
            }
        }
        return discovered;
    }

    /**
     * The results of the test runs Azure DevOps recorded for this pipeline run, which exist when the
     * pipeline publishes them (PublishTestResults@2). A pipeline run id is its build id.
     */
    @Override
    public PulledResults fetchTestResults(DecryptedConfig config, StatusQuery query, int limit) {
        String project = projectApi(config, query.repoRef());
        JsonNode testRuns = getJson(config, project + "/_apis/test/runs?buildUri="
                + encode("vstfs:///Build/Build/" + query.externalRunId()) + version(config, "&"));
        List<CiResult> results = new ArrayList<>();
        for (JsonNode testRun : values(testRuns)) {
            String testRunId = text(testRun, "id");
            if (testRunId != null && collect(config, project, testRunId, results, limit)) {
                return new PulledResults(results, true);
            }
        }
        return new PulledResults(results, false);
    }

    /** @return true when the limit was reached with results still left */
    private boolean collect(DecryptedConfig config, String project, String testRunId, List<CiResult> results,
                            int limit) {
        for (int skip = 0; ; skip += RESULT_PAGE_SIZE) {
            JsonNode page = getJson(config, project + "/_apis/test/Runs/" + encodePath(testRunId)
                    + "/results?$top=" + RESULT_PAGE_SIZE + "&$skip=" + skip + version(config, "&"));
            List<JsonNode> rows = values(page);
            for (JsonNode row : rows) {
                if (results.size() >= limit) {
                    return true;
                }
                results.add(AzureTestOutcomes.toCiResult(row));
            }
            if (rows.size() < RESULT_PAGE_SIZE) {
                return false;
            }
        }
    }

    /** Server 2019 and 2020 stop at api-version 5.0 and 6.0. */
    @Override
    protected String failureHint(HttpResponse<String> response) {
        String body = response.body();
        if (response.statusCode() == HTTP_BAD_REQUEST && body != null
                && body.toLowerCase(Locale.ROOT).contains("api-version")) {
            return "the server does not support this API version; set it on the build server "
                    + "(6.0 for Server 2020, 5.0 for Server 2019)";
        }
        return null;
    }

    // ---- helpers --------------------------------------------------------------------------------

    private HttpRequest post(DecryptedConfig config, String url, Map<String, Object> payload) {
        return request(config, url)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(serialize(payload), StandardCharsets.UTF_8))
                .build();
    }

    private static Map<String, Object> runPayload(TriggerSpec spec, boolean withParameters) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (spec.ref() != null && !spec.ref().isBlank()) {
            payload.put("resources", Map.of("repositories", Map.of("self", Map.of("refName", refName(spec.ref())))));
        }
        if (!withParameters) {
            return payload;
        }
        Map<String, String> templateParameters = new LinkedHashMap<>();
        Map<String, Object> variables = new LinkedHashMap<>();
        spec.parameters().forEach((key, value) -> {
            if (key.startsWith(CORRELATION_PREFIX)) {
                variables.put(key, Map.of("value", value));
            } else {
                templateParameters.put(key, value);
            }
        });
        if (!templateParameters.isEmpty()) {
            payload.put("templateParameters", templateParameters);
        }
        if (!variables.isEmpty()) {
            payload.put("variables", variables);
        }
        return payload;
    }

    /** A bare branch name is a branch; anything already under {@code refs/} is passed as given. */
    static String refName(String ref) {
        return ref.startsWith("refs/") ? ref : "refs/heads/" + ref;
    }

    static PipelineRunStatus mapStatus(String state, String result) {
        if (state == null) {
            return PipelineRunStatus.PENDING;
        }
        return switch (state.toLowerCase(Locale.ROOT)) {
            case "inprogress", "canceling" -> PipelineRunStatus.RUNNING;
            case "completed" -> completedStatus(result);
            default -> PipelineRunStatus.PENDING;
        };
    }

    /** A partially succeeded run had failures; unknown or missing results are not a success either. */
    private static PipelineRunStatus completedStatus(String result) {
        if (result == null) {
            return PipelineRunStatus.FAILED;
        }
        return switch (result.toLowerCase(Locale.ROOT)) {
            case "succeeded" -> PipelineRunStatus.SUCCESS;
            case "canceled" -> PipelineRunStatus.CANCELLED;
            default -> PipelineRunStatus.FAILED;
        };
    }

    private String webUrl(DecryptedConfig config, String repoRef, JsonNode run, String id) {
        JsonNode href = run.path("_links").path("web").get("href");
        if (href != null && !href.isNull()) {
            return href.asString();
        }
        return projectApi(config, repoRef) + "/_build/results?buildId=" + encode(id);
    }

    private static String displayName(JsonNode pipeline) {
        String name = text(pipeline, "name");
        String folder = text(pipeline, "folder");
        String path = folder == null ? "" : folder.replace('\\', '/').replaceAll("^/+", "");
        return path.isEmpty() ? name : path + "/" + name;
    }

    private static List<JsonNode> values(JsonNode body) {
        JsonNode values = body.get("value");
        if (values == null || !values.isArray()) {
            throw new UpstreamServiceException("Azure DevOps returned an unexpected list response");
        }
        List<JsonNode> list = new ArrayList<>();
        values.forEach(list::add);
        return list;
    }

    private String requireId(JsonNode run) {
        String id = text(run, "id");
        if (id == null) {
            throw new UpstreamServiceException("Azure DevOps run response is missing an id");
        }
        return id;
    }

    private String pipelineApi(DecryptedConfig config, String project, String pipelineId) {
        if (pipelineId == null || !pipelineId.matches("\\d+")) {
            throw new IllegalArgumentException(
                    "Azure DevOps pipelines are addressed by their numeric id, got: " + pipelineId);
        }
        return projectApi(config, project) + "/_apis/pipelines/" + pipelineId;
    }

    private String projectApi(DecryptedConfig config, String project) {
        if (project == null || project.isBlank()) {
            throw new IllegalArgumentException("The Azure DevOps project is required");
        }
        return base(config) + "/" + encodePath(project);
    }

    private static String base(DecryptedConfig config) {
        return trimTrailingSlash(config.baseUrl());
    }

    /** {@code api-version} as the first ({@code ?}) or a further ({@code &}) query parameter. */
    private static String version(DecryptedConfig config, String separator) {
        String configured = config.config().getApiVersion();
        String apiVersion = configured == null || configured.isBlank() ? DEFAULT_API_VERSION : configured;
        return separator + "api-version=" + encode(apiVersion);
    }
}
