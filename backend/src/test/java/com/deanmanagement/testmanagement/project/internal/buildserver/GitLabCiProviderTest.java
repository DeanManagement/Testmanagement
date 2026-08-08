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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the GitLab CI adapter against a local stub of the v4 API: trigger payload shape,
 * status mapping, path encoding, and the failure taxonomy the poller's backoff depends on.
 */
class GitLabCiProviderTest {

    private HttpServer server;
    private GitLabCiProvider provider;
    private BuildServerProvider.DecryptedConfig config;

    private final AtomicInteger responseCode = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("{}");
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastToken = new AtomicReference<>();
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().getRawPath());
            lastMethod.set(exchange.getRequestMethod());
            lastToken.set(exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN"));
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseCode.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        BuildServerProperties properties = new BuildServerProperties(
                true, false, 2000, 3000, 3600000L, 20, 120, null);
        provider = new GitLabCiProvider(properties, new ObjectMapper());

        BuildServerConfig entity = new BuildServerConfig();
        entity.setProvider(BuildServerProviderType.GITLAB_CI);
        entity.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        config = new BuildServerProvider.DecryptedConfig(entity, "test-token");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private BuildServerProvider.TriggerSpec spec(Map<String, String> parameters) {
        return new BuildServerProvider.TriggerSpec("group/project", null, "main", parameters,
                UUID.randomUUID());
    }

    @Test
    void trigger_encodesProjectPathAndSendsVariables() {
        responseBody.set("""
                {"id": 4711, "status": "created", "web_url": "https://gitlab.test/group/project/-/pipelines/4711"}
                """);

        BuildServerProvider.TriggerResult result =
                provider.trigger(config, spec(Map.of("TM_PIPELINE_RUN_ID", "abc")));

        assertThat(lastMethod.get()).isEqualTo("POST");
        assertThat(lastPath.get()).isEqualTo("/api/v4/projects/group%2Fproject/pipeline");
        assertThat(lastToken.get()).isEqualTo("test-token");
        assertThat(lastRequestBody.get()).contains("\"ref\":\"main\"");
        assertThat(lastRequestBody.get()).contains("\"key\":\"TM_PIPELINE_RUN_ID\"");
        assertThat(result.externalRunId()).isEqualTo("4711");
        assertThat(result.status()).isEqualTo(PipelineRunStatus.PENDING);
        assertThat(result.externalUrl()).contains("/pipelines/4711");
    }

    @Test
    void trigger_withoutRef_isCallerError() {
        BuildServerProvider.TriggerSpec noRef = new BuildServerProvider.TriggerSpec(
                "group/project", null, null, Map.of(), UUID.randomUUID());
        assertThatThrownBy(() -> provider.trigger(config, noRef))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ref");
    }

    @Test
    void fetchStatus_mapsRunningAndTerminalStates() {
        responseBody.set("{\"id\": 4711, \"status\": \"running\", \"web_url\": \"https://g/p/4711\"}");
        BuildServerProvider.StatusQuery query = new BuildServerProvider.StatusQuery(
                "group/project", null, "4711", "main", UUID.randomUUID(), Instant.now());

        assertThat(provider.fetchStatus(config, query).status()).isEqualTo(PipelineRunStatus.RUNNING);

        responseBody.set("{\"id\": 4711, \"status\": \"success\"}");
        assertThat(provider.fetchStatus(config, query).status()).isEqualTo(PipelineRunStatus.SUCCESS);

        responseBody.set("{\"id\": 4711, \"status\": \"failed\"}");
        assertThat(provider.fetchStatus(config, query).status()).isEqualTo(PipelineRunStatus.FAILED);

        responseBody.set("{\"id\": 4711, \"status\": \"canceled\"}");
        assertThat(provider.fetchStatus(config, query).status()).isEqualTo(PipelineRunStatus.CANCELLED);
    }

    @Test
    void authFailure_isDistinguishable() {
        responseCode.set(401);
        assertThatThrownBy(() -> provider.trigger(config, spec(Map.of())))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("rejected the configured access token");
    }

    @Test
    void rateLimit_isDistinguishable() {
        responseCode.set(429);
        assertThatThrownBy(() -> provider.trigger(config, spec(Map.of())))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("rate limit");
    }

    @Test
    void malformedResponse_doesNotEchoContent() {
        responseBody.set("<html>surprise</html>");
        assertThatThrownBy(() -> provider.trigger(config, spec(Map.of())))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("malformed")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("surprise"));
    }

    @Test
    void discover_listsBranchesAsTriggerTargets() {
        responseBody.set("""
                [{"name": "main"}, {"name": "release/1.0"}]
                """);

        List<BuildServerProvider.DiscoveredWorkflow> discovered =
                provider.discover(config, "group/project");

        assertThat(lastPath.get()).isEqualTo("/api/v4/projects/group%2Fproject/repository/branches");
        assertThat(discovered).hasSize(2);
        assertThat(discovered.getFirst().defaultRef()).isEqualTo("main");
        assertThat(discovered.getFirst().repoRef()).isEqualTo("group/project");
    }
}
