package com.deanmanagement.testmanagement.project.internal.issuetracker;

import com.deanmanagement.testmanagement.project.internal.entity.IssueState;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the GitLab adapter against a local stub of the v4 API. The cases that matter are the
 * failure modes — auth rejection, rate limiting, transport failure — because those are what the
 * config UI and the poller's backoff depend on being distinguishable.
 */
class GitLabIssueProviderTest {

    private HttpServer server;
    private GitLabIssueProvider provider;
    private IssueTrackerProvider.DecryptedConfig config;

    private final AtomicInteger responseCode = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("{}");
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastToken = new AtomicReference<>();
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            // Raw, not decoded: the point of the assertion is that the provider percent-encodes
            // the project ref, and getPath() would silently undo exactly that.
            lastPath.set(exchange.getRequestURI().getRawPath());
            lastQuery.set(exchange.getRequestURI().getQuery());
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

        IssueTrackerProperties properties = new IssueTrackerProperties(
                null, true, false, 2000, 3000, 3600000L, 50, 0L);
        provider = new GitLabIssueProvider(properties, new ObjectMapper());

        IssueTrackerConfig entity = new IssueTrackerConfig();
        entity.setProvider(IssueTrackerProviderType.GITLAB);
        entity.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        entity.setProjectRef("group/project");
        config = new IssueTrackerProvider.DecryptedConfig(entity, "test-token");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void search_urlEncodesProjectRefAndSendsToken() {
        responseBody.set("""
                [{"iid": 7, "title": "Login fails", "state": "opened", "web_url": "https://gitlab.test/group/project/-/issues/7"}]
                """);

        List<Issue> issues = provider.search(config, "login");

        assertThat(lastPath.get()).isEqualTo("/api/v4/projects/group%2Fproject/issues");
        assertThat(lastQuery.get()).contains("search=login");
        assertThat(lastToken.get()).isEqualTo("test-token");
        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().externalId()).isEqualTo("group/project#7");
        assertThat(issues.getFirst().state()).isEqualTo(IssueState.OPEN);
        assertThat(issues.getFirst().title()).isEqualTo("Login fails");
    }

    @Test
    void create_postsTitleAndDescription() {
        responseBody.set("""
                {"iid": 12, "title": "New defect", "state": "opened", "web_url": "https://gitlab.test/i/12"}
                """);
        responseCode.set(201);

        Issue issue = provider.create(config, new IssueDraft("New defect", "Body text"));

        assertThat(lastMethod.get()).isEqualTo("POST");
        assertThat(lastRequestBody.get()).contains("\"title\":\"New defect\"");
        assertThat(lastRequestBody.get()).contains("\"description\":\"Body text\"");
        assertThat(issue.externalId()).isEqualTo("group/project#12");
        assertThat(issue.url()).isEqualTo("https://gitlab.test/i/12");
    }

    @Test
    void get_stripsProjectPrefixToReachTheIid() {
        responseBody.set("""
                {"iid": 7, "title": "Login fails", "state": "closed", "web_url": "https://gitlab.test/i/7"}
                """);

        Issue issue = provider.get(config, "group/project#7");

        assertThat(lastPath.get()).isEqualTo("/api/v4/projects/group%2Fproject/issues/7");
        assertThat(issue.state()).isEqualTo(IssueState.CLOSED);
    }

    @Test
    void get_rejectsAReferenceThatIsNotAnIid() {
        assertThatThrownBy(() -> provider.get(config, "group/project#not-a-number"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid GitLab issue reference");
    }

    @Test
    void unknownStateBecomesUnknownRatherThanGuessing() {
        responseBody.set("""
                {"iid": 3, "title": "Odd", "state": "something-new", "web_url": "https://gitlab.test/i/3"}
                """);

        assertThat(provider.get(config, "group/project#3").state()).isEqualTo(IssueState.UNKNOWN);
    }

    @Test
    void authFailureIsReportedAsAnUpstreamError() {
        responseCode.set(401);
        responseBody.set("{\"message\":\"401 Unauthorized\"}");

        assertThatThrownBy(() -> provider.search(config, "x"))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("rejected the configured access token");
    }

    @Test
    void missingProjectIsDistinguishedFromAuthFailure() {
        responseCode.set(404);

        assertThatThrownBy(() -> provider.search(config, "x"))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("was not found");
    }

    @Test
    void rateLimitingIsReportedDistinctly() {
        responseCode.set(429);

        assertThatThrownBy(() -> provider.search(config, "x"))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("rate limit");
    }

    @Test
    void malformedJsonDoesNotLeakAParserStackTrace() {
        responseBody.set("not json at all");

        assertThatThrownBy(() -> provider.search(config, "x"))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("malformed response");
    }

    @Test
    void unreachableHostIsReportedWithoutTheToken() {
        server.stop(0);

        assertThatThrownBy(() -> provider.search(config, "x"))
                .isInstanceOf(UpstreamServiceException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("test-token"));
    }

    @Test
    void testConnection_hitsTheProjectEndpoint() {
        responseBody.set("{\"id\": 1, \"path_with_namespace\": \"group/project\"}");

        provider.testConnection(config);

        assertThat(lastPath.get()).isEqualTo("/api/v4/projects/group%2Fproject");
    }
}
