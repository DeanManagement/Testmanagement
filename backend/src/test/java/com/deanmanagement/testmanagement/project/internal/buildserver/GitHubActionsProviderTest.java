package com.deanmanagement.testmanagement.project.internal.buildserver;

import com.deanmanagement.testmanagement.project.internal.entity.BuildServerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.BuildServerProviderType;
import com.deanmanagement.testmanagement.project.internal.entity.PipelineRunStatus;
import com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The dispatch-without-id model (PRD-024's awkward spot): a 204 dispatch yields no run id, a 422
 * for undeclared inputs is retried bare, and the run is recovered afterwards from the run listing
 * — exactly by run-name when the workflow uses the documented convention, newest-plausible
 * otherwise, and "no verdict" when nothing is visible yet.
 */
class GitHubActionsProviderTest {

    private HttpServer server;
    private GitHubActionsProvider provider;
    private BuildServerProvider.DecryptedConfig config;

    private final AtomicInteger dispatchCode = new AtomicInteger(204);
    private final AtomicReference<String> runsBody = new AtomicReference<>("{\"workflow_runs\": []}");
    private final List<String> dispatchBodies = new CopyOnWriteArrayList<>();

    private final UUID pipelineRunId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            String body = "{}";
            int status = 200;
            if (path.endsWith("/dispatches")) {
                dispatchBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                status = dispatchCode.get();
                // A second attempt without inputs succeeds, mimicking GitHub's 422 semantics.
                if (status == 422 && dispatchBodies.size() > 1
                        && !dispatchBodies.getLast().contains("inputs")) {
                    status = 204;
                }
                body = "";
            } else if (path.contains("/runs")) {
                body = runsBody.get();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();

        BuildServerProperties properties = new BuildServerProperties(
                true, false, 2000, 3000, 3600000L, 20, 120, null);
        provider = new GitHubActionsProvider(properties, new ObjectMapper());

        BuildServerConfig entity = new BuildServerConfig();
        entity.setProvider(BuildServerProviderType.GITHUB_ACTIONS);
        entity.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        config = new BuildServerProvider.DecryptedConfig(entity, "gh-token");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private BuildServerProvider.TriggerSpec spec() {
        return new BuildServerProvider.TriggerSpec("owner/repo", "tests.yml", "main",
                Map.of("TM_PIPELINE_RUN_ID", pipelineRunId.toString()), pipelineRunId);
    }

    private BuildServerProvider.StatusQuery uncorrelated() {
        return new BuildServerProvider.StatusQuery("owner/repo", "tests.yml", null, "main",
                pipelineRunId, Instant.now().minusSeconds(10));
    }

    @Test
    void dispatch_returnsNoRunId() {
        BuildServerProvider.TriggerResult result = provider.trigger(config, spec());

        assertThat(result.externalRunId()).isNull();
        assertThat(result.status()).isEqualTo(PipelineRunStatus.TRIGGERED);
        assertThat(dispatchBodies.getFirst()).contains("TM_PIPELINE_RUN_ID");
    }

    @Test
    void dispatch_undeclaredInputs_isRetriedWithoutThem() {
        dispatchCode.set(422);

        BuildServerProvider.TriggerResult result = provider.trigger(config, spec());

        assertThat(result.status()).isEqualTo(PipelineRunStatus.TRIGGERED);
        assertThat(dispatchBodies).hasSize(2);
        assertThat(dispatchBodies.getLast()).doesNotContain("inputs");
    }

    @Test
    void dispatch_missingWorkflowDispatchTrigger_failsWithClearMessage() throws IOException {
        server.stop(0);
        // A stub that always answers 422, so the bare retry fails too.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(422, -1);
            exchange.close();
        });
        server.start();
        config.config().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());

        assertThatThrownBy(() -> provider.trigger(config, spec()))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("workflow_dispatch");
    }

    @Test
    void correlation_prefersRunNameCarryingThePipelineRunId() {
        runsBody.set("""
                {"workflow_runs": [
                  {"id": 900, "display_title": "unrelated newer run", "status": "queued",
                   "head_branch": "main", "created_at": "%s", "html_url": "https://gh/run/900"},
                  {"id": 800, "display_title": "TM %s", "status": "in_progress",
                   "head_branch": "main", "created_at": "%s", "html_url": "https://gh/run/800"}
                ]}
                """.formatted(Instant.now(), pipelineRunId, Instant.now().minusSeconds(2)));

        BuildServerProvider.StatusResult result = provider.fetchStatus(config, uncorrelated());

        assertThat(result.externalRunId()).isEqualTo("800");
        assertThat(result.status()).isEqualTo(PipelineRunStatus.RUNNING);
        assertThat(result.externalUrl()).isEqualTo("https://gh/run/800");
    }

    @Test
    void correlation_withoutRunName_takesNewestPlausibleCandidate() {
        runsBody.set("""
                {"workflow_runs": [
                  {"id": 700, "display_title": "CI", "status": "queued", "head_branch": "other",
                   "created_at": "%s"},
                  {"id": 701, "display_title": "CI", "status": "queued", "head_branch": "main",
                   "created_at": "%s"}
                ]}
                """.formatted(Instant.now(), Instant.now()));

        BuildServerProvider.StatusResult result = provider.fetchStatus(config, uncorrelated());

        // The run on another branch is not a candidate.
        assertThat(result.externalRunId()).isEqualTo("701");
    }

    @Test
    void correlation_runNotVisibleYet_keepsStoredStatus() {
        runsBody.set("{\"workflow_runs\": []}");

        BuildServerProvider.StatusResult result = provider.fetchStatus(config, uncorrelated());

        assertThat(result.status()).isNull();
        assertThat(result.externalRunId()).isNull();
    }

    @Test
    void correlation_ignoresRunsOlderThanTheTrigger() {
        runsBody.set("""
                {"workflow_runs": [
                  {"id": 600, "display_title": "old run", "status": "completed", "conclusion": "success",
                   "head_branch": "main", "created_at": "%s"}
                ]}
                """.formatted(Instant.now().minusSeconds(600)));

        BuildServerProvider.StatusResult result = provider.fetchStatus(config, uncorrelated());

        assertThat(result.status()).isNull();
    }

    @Test
    void knownRun_mapsCompletedConclusion() {
        runsBody.set("{\"id\": 800, \"status\": \"completed\", \"conclusion\": \"failure\","
                + " \"html_url\": \"https://gh/run/800\"}");
        BuildServerProvider.StatusQuery known = new BuildServerProvider.StatusQuery(
                "owner/repo", "tests.yml", "800", "main", pipelineRunId, Instant.now());

        BuildServerProvider.StatusResult result = provider.fetchStatus(config, known);

        assertThat(result.status()).isEqualTo(PipelineRunStatus.FAILED);
    }
}
