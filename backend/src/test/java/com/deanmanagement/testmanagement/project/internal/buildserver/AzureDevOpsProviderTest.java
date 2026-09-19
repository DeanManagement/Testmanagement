package com.deanmanagement.testmanagement.project.internal.buildserver;

import com.deanmanagement.testmanagement.project.internal.StubHttpServer;
import com.deanmanagement.testmanagement.project.internal.StubHttpServer.Response;
import com.deanmanagement.testmanagement.project.internal.entity.BuildServerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.BuildServerProviderType;
import com.deanmanagement.testmanagement.project.internal.entity.PipelineRunStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PRD-026 §3.2: the Azure Pipelines adapter against a stub of the REST API, including the traps
 * the PRD names: a rejected PAT answered with 203 and HTML, and template parameters or queue-time
 * variables the pipeline does not accept.
 */
class AzureDevOpsProviderTest {

    private final ObjectMapper json = new ObjectMapper();
    private StubHttpServer server;
    private AzureDevOpsProvider provider;
    private BuildServerConfig entity;
    private BuildServerProvider.DecryptedConfig config;

    @BeforeEach
    void setUp() throws IOException {
        server = new StubHttpServer();
        provider = new AzureDevOpsProvider(new BuildServerProperties(true, false, 2000, 3000, 3600000L, 20, 120, null),
                json);
        entity = new BuildServerConfig();
        entity.setProvider(BuildServerProviderType.AZURE_DEVOPS);
        entity.setBaseUrl(server.baseUrl() + "/contoso/");
        config = new BuildServerProvider.DecryptedConfig(entity, "pat-123");
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private BuildServerProvider.TriggerSpec spec() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("suite", "smoke");
        parameters.put("TM_PIPELINE_RUN_ID", "run-1");
        return new BuildServerProvider.TriggerSpec("Payments Team", "42", "main", parameters, UUID.randomUUID());
    }

    private BuildServerProvider.StatusQuery query(String runId) {
        return new BuildServerProvider.StatusQuery("Payments Team", "42", runId, "main", UUID.randomUUID(), null);
    }

    @Test
    void triggersThePipelineWithTheBranchParametersAndCorrelationVariable() {
        server.respond(Response.json("{\"id\":17,\"state\":\"inProgress\",\"_links\":{\"web\":{\"href\":\"https://web/17\"}}}"));

        BuildServerProvider.TriggerResult result = provider.trigger(config, spec());

        StubHttpServer.Request request = server.lastRequest();
        assertThat(request.pathAndQuery()).isEqualTo("/contoso/Payments%20Team/_apis/pipelines/42/runs?api-version=7.1");
        assertThat(request.authorization()).isEqualTo("Basic "
                + Base64.getEncoder().encodeToString(":pat-123".getBytes(StandardCharsets.UTF_8)));
        JsonNode body = json.readTree(request.body());
        assertThat(body.at("/resources/repositories/self/refName").asString()).isEqualTo("refs/heads/main");
        assertThat(body.at("/templateParameters/suite").asString()).isEqualTo("smoke");
        assertThat(body.at("/variables/TM_PIPELINE_RUN_ID/value").asString()).isEqualTo("run-1");
        assertThat(result).isEqualTo(new BuildServerProvider.TriggerResult(PipelineRunStatus.RUNNING, "17", "https://web/17"));
    }

    @Test
    void aRejectedTriggerIsRetriedOnceWithoutParametersOrVariables() {
        server.respond(Response.json(400, "{\"message\":\"Unexpected parameter 'suite'\"}"),
                Response.json("{\"id\":18,\"state\":\"unknown\"}"));

        BuildServerProvider.TriggerResult result = provider.trigger(config, spec());

        JsonNode retry = json.readTree(server.lastRequest().body());
        assertThat(server.requests()).hasSize(2);
        assertThat(retry.has("templateParameters")).isFalse();
        assertThat(retry.has("variables")).isFalse();
        assertThat(retry.at("/resources/repositories/self/refName").asString()).isEqualTo("refs/heads/main");
        assertThat(result.externalRunId()).isEqualTo("18");
        assertThat(result.externalUrl()).endsWith("/contoso/Payments%20Team/_build/results?buildId=18");
    }

    @Test
    void aSecondRejectionFailsTheTrigger() {
        server.respond(Response.json(400, "{}"), Response.json(400, "{}"));

        assertThatThrownBy(() -> provider.trigger(config, spec()))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("HTTP 400");
    }

    @Test
    void aSignInPageAnsweredWith203IsARejectedToken() {
        server.respond(new Response(203, "text/html; charset=utf-8", "<html>Sign in</html>"));

        assertThatThrownBy(() -> provider.fetchStatus(config, query("17")))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("rejected the configured access token");
    }

    @Test
    void anUnsupportedApiVersionSaysWhichSettingToChange() {
        entity.setApiVersion("7.1");
        server.respond(Response.json(400, "{\"message\":\"The requested REST API version of 7.1 is out of range for this server. api-version\"}"));

        assertThatThrownBy(() -> provider.testConnection(config))
                .hasMessageContaining("6.0 for Server 2020");
    }

    @Test
    void usesTheConfiguredApiVersion() {
        entity.setApiVersion("6.0");
        server.respond(Response.json("{\"value\":[]}"));

        provider.testConnection(config);

        assertThat(server.lastRequest().pathAndQuery()).isEqualTo("/contoso/_apis/projects?$top=1&api-version=6.0");
    }

    @ParameterizedTest
    @CsvSource({
            "unknown,,PENDING",
            "inProgress,,RUNNING",
            "canceling,,RUNNING",
            "completed,succeeded,SUCCESS",
            "completed,failed,FAILED",
            "completed,partiallySucceeded,FAILED",
            "completed,canceled,CANCELLED",
            "completed,,FAILED"
    })
    void mapsStateAndResult(String state, String result, PipelineRunStatus expected) {
        assertThat(AzureDevOpsProvider.mapStatus(state, result)).isEqualTo(expected);
    }

    @Test
    void discoversPipelinesWithTheirFolder() {
        server.respond(Response.json("{\"value\":[{\"id\":42,\"name\":\"CI\",\"folder\":\"\\\\\"},"
                + "{\"id\":43,\"name\":\"Nightly\",\"folder\":\"\\\\Release\\\\QA\"}]}"));

        var discovered = provider.discover(config, "Payments Team");

        assertThat(discovered).containsExactly(
                new BuildServerProvider.DiscoveredWorkflow("CI", "Payments Team", "42", null),
                new BuildServerProvider.DiscoveredWorkflow("Release/QA/Nightly", "Payments Team", "43", null));
    }

    @Test
    void aPipelineIsAddressedByItsNumericId() {
        var byName = new BuildServerProvider.TriggerSpec("Payments", "CI", "main", Map.of(), UUID.randomUUID());

        assertThatThrownBy(() -> provider.trigger(config, byName)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pullsTheResultsOfEveryTestRunOfTheBuild() {
        server.respond(
                Response.json("{\"value\":[{\"id\":501}]}"),
                Response.json("{\"value\":[{\"outcome\":\"Passed\",\"testCaseTitle\":\"Login\",\"automatedTestStorage\":\"web.dll\",\"durationInMs\":1200.4},"
                        + "{\"outcome\":\"Failed\",\"automatedTestName\":\"Pay.Checkout\",\"errorMessage\":\"500\"}]}"));

        BuildServerProvider.PulledResults pulled = provider.fetchTestResults(config, query("17"), 100);

        assertThat(server.requests().get(0).pathAndQuery()).isEqualTo(
                "/contoso/Payments%20Team/_apis/test/runs?buildUri=vstfs%3A%2F%2F%2FBuild%2FBuild%2F17&api-version=7.1");
        assertThat(server.requests().get(1).pathAndQuery()).isEqualTo(
                "/contoso/Payments%20Team/_apis/test/Runs/501/results?$top=200&$skip=0&api-version=7.1");
        assertThat(pulled.truncated()).isFalse();
        assertThat(pulled.results()).extracting("title", "status", "suiteName", "durationMs").containsExactly(
                org.assertj.core.groups.Tuple.tuple("Login", TestResultStatus.PASSED, "web.dll", 1200L),
                org.assertj.core.groups.Tuple.tuple("Pay.Checkout", TestResultStatus.FAILED, null, null));
    }

    @Test
    void stopsAtTheLimitAndSaysSo() {
        server.respond(Response.json("{\"value\":[{\"id\":501}]}"),
                Response.json("{\"value\":[{\"outcome\":\"Passed\",\"testCaseTitle\":\"A\"},{\"outcome\":\"Passed\",\"testCaseTitle\":\"B\"}]}"));

        BuildServerProvider.PulledResults pulled = provider.fetchTestResults(config, query("17"), 1);

        assertThat(pulled.results()).hasSize(1);
        assertThat(pulled.truncated()).isTrue();
    }

    @Test
    void aBuildWithoutPublishedResultsPullsNothing() {
        server.respond(Response.json("{\"value\":[]}"));

        assertThat(provider.fetchTestResults(config, query("17"), 100).results()).isEmpty();
        assertThat(server.requests()).hasSize(1);
    }
}
