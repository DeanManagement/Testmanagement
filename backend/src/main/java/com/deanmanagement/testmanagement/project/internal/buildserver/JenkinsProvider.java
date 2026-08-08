package com.deanmanagement.testmanagement.project.internal.buildserver;

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
import java.util.List;
import java.util.Map;

/**
 * Jenkins adapter (PRD-024). Jobs are addressed by their full path — {@code folder/subfolder/job}
 * in the workflow's {@code repoRef} — which becomes Jenkins' {@code /job/folder/job/subfolder/…}
 * URL shape here.
 *
 * <p>Identification is two-step: {@code buildWithParameters} answers with a queue-item
 * {@code Location} rather than a build, so the external id is stored as {@code queue:<n>} until
 * the queue item names its executable, at which point it becomes the build number. Authentication
 * is HTTP Basic with an API token; the stored secret is {@code user:apiToken} and using a token
 * (not a password) is what exempts the call from Jenkins' CSRF crumb requirement.
 */
@Component
public class JenkinsProvider extends HttpBuildProviderSupport implements BuildServerProvider {

    private static final String QUEUE_PREFIX = "queue:";

    public JenkinsProvider(BuildServerProperties properties, ObjectMapper objectMapper) {
        super(properties, objectMapper);
    }

    @Override
    public BuildServerProviderType type() {
        return BuildServerProviderType.JENKINS;
    }

    @Override
    protected String providerName() {
        return "Jenkins";
    }

    @Override
    protected HttpRequest.Builder authenticate(HttpRequest.Builder builder, String token) {
        if (token.indexOf(':') <= 0) {
            throw new IllegalArgumentException(
                    "The Jenkins credential must be stored as user:apiToken");
        }
        String encoded = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
        return builder.header("Authorization", "Basic " + encoded);
    }

    @Override
    public TriggerResult trigger(DecryptedConfig config, TriggerSpec spec) {
        // Parameters go in the query string, the documented buildWithParameters contract.
        // Jenkins drops submitted parameters the job does not declare, so the TM_* variables are
        // harmless on jobs that ignore them.
        StringBuilder url = new StringBuilder(jobApi(config, spec.repoRef())).append("/buildWithParameters");
        char separator = '?';
        for (Map.Entry<String, String> parameter : spec.parameters().entrySet()) {
            url.append(separator).append(encode(parameter.getKey()))
                    .append('=').append(encode(parameter.getValue()));
            separator = '&';
        }
        HttpRequest request = request(config, url.toString())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = send(request, config);

        String queueId = queueIdFrom(response);
        return new TriggerResult(PipelineRunStatus.PENDING,
                queueId != null ? QUEUE_PREFIX + queueId : null, null);
    }

    @Override
    public StatusResult fetchStatus(DecryptedConfig config, StatusQuery query) {
        String externalRunId = query.externalRunId();
        if (externalRunId == null) {
            throw new UpstreamServiceException("Jenkins queue reference is missing; cannot poll status");
        }
        if (externalRunId.startsWith(QUEUE_PREFIX)) {
            return resolveQueueItem(config, query, externalRunId.substring(QUEUE_PREFIX.length()));
        }
        JsonNode build = getJson(config, jobApi(config, query.repoRef()) + "/"
                + encodePath(externalRunId) + "/api/json?tree=building,result,url");
        return new StatusResult(mapBuild(build), null, text(build, "url"));
    }

    private StatusResult resolveQueueItem(DecryptedConfig config, StatusQuery query, String queueId) {
        JsonNode item = getJson(config, trimTrailingSlash(config.baseUrl())
                + "/queue/item/" + encodePath(queueId) + "/api/json");
        JsonNode executable = item.get("executable");
        if (executable == null || executable.isNull()) {
            // Still queued; a cancelled queue item reports why.
            JsonNode cancelled = item.get("cancelled");
            if (cancelled != null && cancelled.asBoolean(false)) {
                return new StatusResult(PipelineRunStatus.CANCELLED, null, null);
            }
            return new StatusResult(PipelineRunStatus.PENDING, null, null);
        }
        JsonNode number = executable.get("number");
        if (number == null || number.isNull()) {
            throw new UpstreamServiceException("Jenkins queue item names an executable without a number");
        }
        // The build has started: upgrade the stored id from queue:<n> to the build number and
        // report its live status in the same pass.
        StatusQuery upgraded = new StatusQuery(query.repoRef(), query.workflowRef(),
                number.asString(), query.triggeredRef(), query.pipelineRunId(), query.triggeredAt());
        StatusResult status = fetchStatus(config, upgraded);
        return new StatusResult(status.status(), number.asString(),
                status.externalUrl() != null ? status.externalUrl() : text(executable, "url"));
    }

    @Override
    public void testConnection(DecryptedConfig config) {
        getJson(config, trimTrailingSlash(config.baseUrl()) + "/api/json?tree=jobs[name]");
    }

    /** Lists jobs one folder level deep, which covers the common folder-per-team layout. */
    @Override
    public List<DiscoveredWorkflow> discover(DecryptedConfig config, String repoRef) {
        String root = repoRef == null || repoRef.isBlank()
                ? trimTrailingSlash(config.baseUrl())
                : jobApi(config, repoRef);
        JsonNode body = getJson(config, root + "/api/json?tree=jobs[name,fullName,jobs[name,fullName]]");
        List<DiscoveredWorkflow> discovered = new ArrayList<>();
        collectJobs(body.get("jobs"), discovered);
        return discovered;
    }

    private void collectJobs(JsonNode jobs, List<DiscoveredWorkflow> discovered) {
        if (jobs == null || !jobs.isArray()) {
            return;
        }
        for (JsonNode job : jobs) {
            JsonNode children = job.get("jobs");
            if (children != null && children.isArray()) {
                collectJobs(children, discovered);
                continue;
            }
            String fullName = text(job, "fullName");
            String name = text(job, "name");
            if (fullName != null) {
                discovered.add(new DiscoveredWorkflow(name != null ? name : fullName,
                        fullName, null, null));
            }
        }
    }

    private String jobApi(DecryptedConfig config, String jobPath) {
        StringBuilder url = new StringBuilder(trimTrailingSlash(config.baseUrl()));
        for (String segment : jobPath.split("/")) {
            if (segment.isBlank()) {
                throw new IllegalArgumentException("Not a valid Jenkins job path: " + jobPath);
            }
            url.append("/job/").append(encodePath(segment));
        }
        return url.toString();
    }

    private static String queueIdFrom(HttpResponse<String> response) {
        // Location: {base}/queue/item/123/
        return response.headers().firstValue("Location")
                .map(location -> {
                    String trimmed = trimTrailingSlash(location);
                    int marker = trimmed.lastIndexOf("/queue/item/");
                    if (marker < 0) {
                        return null;
                    }
                    String id = trimmed.substring(marker + "/queue/item/".length());
                    return id.chars().allMatch(Character::isDigit) ? id : null;
                })
                .orElse(null);
    }

    private static PipelineRunStatus mapBuild(JsonNode build) {
        JsonNode building = build.get("building");
        if (building != null && building.asBoolean(false)) {
            return PipelineRunStatus.RUNNING;
        }
        String result = text(build, "result");
        if (result == null) {
            return PipelineRunStatus.RUNNING;
        }
        return switch (result.toUpperCase()) {
            case "SUCCESS" -> PipelineRunStatus.SUCCESS;
            case "FAILURE", "UNSTABLE" -> PipelineRunStatus.FAILED;
            case "ABORTED", "NOT_BUILT" -> PipelineRunStatus.CANCELLED;
            default -> PipelineRunStatus.RUNNING;
        };
    }
}
