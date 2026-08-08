package com.deanmanagement.testmanagement.project.internal.buildserver;

import com.deanmanagement.testmanagement.project.internal.entity.BuildServerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.BuildServerProviderType;
import com.deanmanagement.testmanagement.project.internal.entity.PipelineRunStatus;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Jenkins mechanics against a stub: the two-step queue-item → build-number resolution, folder job
 * path building, basic-auth from the {@code user:apiToken} secret, and result mapping.
 */
class JenkinsProviderTest {

    private HttpServer server;
    private JenkinsProvider provider;
    private BuildServerProvider.DecryptedConfig config;
    private String baseUrl;

    /** Path → [status, body, extra location header]. */
    private final Map<String, String[]> routes = new ConcurrentHashMap<>();
    private final Map<String, String> observedAuth = new ConcurrentHashMap<>();
    private final Map<String, String> observedQuery = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            observedAuth.put(path, exchange.getRequestHeaders().getFirst("Authorization"));
            if (exchange.getRequestURI().getQuery() != null) {
                observedQuery.put(path, exchange.getRequestURI().getQuery());
            }
            String[] route = routes.getOrDefault(path, new String[]{"404", "{}", null});
            if (route[2] != null) {
                exchange.getResponseHeaders().add("Location", route[2]);
            }
            byte[] body = route[1].getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(Integer.parseInt(route[0]), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        BuildServerProperties properties = new BuildServerProperties(
                true, false, 2000, 3000, 3600000L, 20, 120, null);
        provider = new JenkinsProvider(properties, new ObjectMapper());

        BuildServerConfig entity = new BuildServerConfig();
        entity.setProvider(BuildServerProviderType.JENKINS);
        entity.setBaseUrl(baseUrl);
        config = new BuildServerProvider.DecryptedConfig(entity, "jenkins-user:api-token");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void trigger_buildsFolderJobPath_sendsBasicAuth_andParsesQueueLocation() {
        routes.put("/job/team/job/nightly/buildWithParameters",
                new String[]{"201", "", baseUrl + "/queue/item/42/"});

        BuildServerProvider.TriggerResult result = provider.trigger(config,
                new BuildServerProvider.TriggerSpec("team/nightly", null, null,
                        Map.of("TM_PIPELINE_RUN_ID", "abc"), UUID.randomUUID()));

        assertThat(result.externalRunId()).isEqualTo("queue:42");
        assertThat(result.status()).isEqualTo(PipelineRunStatus.PENDING);
        String expectedAuth = "Basic " + Base64.getEncoder()
                .encodeToString("jenkins-user:api-token".getBytes(StandardCharsets.UTF_8));
        assertThat(observedAuth.get("/job/team/job/nightly/buildWithParameters")).isEqualTo(expectedAuth);
        assertThat(observedQuery.get("/job/team/job/nightly/buildWithParameters"))
                .contains("TM_PIPELINE_RUN_ID=abc");
    }

    @Test
    void fetchStatus_upgradesQueueItemToBuildNumber() {
        routes.put("/queue/item/42/api/json",
                new String[]{"200", "{\"executable\": {\"number\": 137, \"url\": \"" + baseUrl
                        + "/job/team/job/nightly/137/\"}}", null});
        routes.put("/job/team/job/nightly/137/api/json",
                new String[]{"200", "{\"building\": true, \"result\": null}", null});

        BuildServerProvider.StatusResult result = provider.fetchStatus(config,
                new BuildServerProvider.StatusQuery("team/nightly", null, "queue:42", null,
                        UUID.randomUUID(), Instant.now()));

        assertThat(result.externalRunId()).isEqualTo("137");
        assertThat(result.status()).isEqualTo(PipelineRunStatus.RUNNING);
    }

    @Test
    void fetchStatus_stillQueued_staysPending() {
        routes.put("/queue/item/42/api/json", new String[]{"200", "{\"why\": \"waiting\"}", null});

        BuildServerProvider.StatusResult result = provider.fetchStatus(config,
                new BuildServerProvider.StatusQuery("team/nightly", null, "queue:42", null,
                        UUID.randomUUID(), Instant.now()));

        assertThat(result.status()).isEqualTo(PipelineRunStatus.PENDING);
        assertThat(result.externalRunId()).isNull();
    }

    @Test
    void fetchStatus_mapsBuildResults() {
        BuildServerProvider.StatusQuery query = new BuildServerProvider.StatusQuery(
                "team/nightly", null, "137", null, UUID.randomUUID(), Instant.now());

        routes.put("/job/team/job/nightly/137/api/json",
                new String[]{"200", "{\"building\": false, \"result\": \"SUCCESS\"}", null});
        assertThat(provider.fetchStatus(config, query).status()).isEqualTo(PipelineRunStatus.SUCCESS);

        routes.put("/job/team/job/nightly/137/api/json",
                new String[]{"200", "{\"building\": false, \"result\": \"UNSTABLE\"}", null});
        assertThat(provider.fetchStatus(config, query).status()).isEqualTo(PipelineRunStatus.FAILED);

        routes.put("/job/team/job/nightly/137/api/json",
                new String[]{"200", "{\"building\": false, \"result\": \"ABORTED\"}", null});
        assertThat(provider.fetchStatus(config, query).status()).isEqualTo(PipelineRunStatus.CANCELLED);
    }

    @Test
    void credentialWithoutUserPart_isCallerError() {
        BuildServerProvider.DecryptedConfig bad =
                new BuildServerProvider.DecryptedConfig(config.config(), "token-only");
        assertThatThrownBy(() -> provider.trigger(bad,
                new BuildServerProvider.TriggerSpec("job", null, null, Map.of(), UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user:apiToken");
    }

    @Test
    void discover_flattensFolderTreeOneLevel() {
        routes.put("/api/json", new String[]{"200", """
                {"jobs": [
                  {"name": "standalone", "fullName": "standalone"},
                  {"name": "team", "fullName": "team", "jobs": [
                    {"name": "nightly", "fullName": "team/nightly"}
                  ]}
                ]}
                """, null});

        var discovered = provider.discover(config, null);

        assertThat(discovered).extracting(BuildServerProvider.DiscoveredWorkflow::repoRef)
                .containsExactly("standalone", "team/nightly");
    }
}
