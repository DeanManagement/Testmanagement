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
 * Exercises the Forgejo adapter against a local stub of the v1 API. Beyond the shared failure
 * modes, the cases specific to this tracker are the two-segment repo path and the fact that its
 * issues endpoint also returns pull requests.
 */
class ForgejoIssueProviderTest {

    private HttpServer server;
    private ForgejoIssueProvider provider;
    private IssueTrackerProvider.DecryptedConfig config;

    private final AtomicInteger responseCode = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("{}");
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().getRawPath());
            lastQuery.set(exchange.getRequestURI().getQuery());
            lastMethod.set(exchange.getRequestMethod());
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
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
        provider = new ForgejoIssueProvider(properties, new ObjectMapper());
        config = configFor("acme/webshop");
    }

    private IssueTrackerProvider.DecryptedConfig configFor(String projectRef) {
        IssueTrackerConfig entity = new IssueTrackerConfig();
        entity.setProvider(IssueTrackerProviderType.FORGEJO);
        entity.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        entity.setProjectRef(projectRef);
        return new IssueTrackerProvider.DecryptedConfig(entity, "forgejo-token");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void search_usesTwoPathSegmentsAndTheTokenScheme() {
        responseBody.set("""
                [{"number": 7, "title": "Cart empties", "state": "open", "html_url": "https://forge.test/acme/webshop/issues/7"}]
                """);

        List<Issue> issues = provider.search(config, "cart");

        // Not %2F: Forgejo addresses repos as /repos/{owner}/{repo}, unlike GitLab's encoded path.
        assertThat(lastPath.get()).isEqualTo("/api/v1/repos/acme/webshop/issues");
        assertThat(lastQuery.get()).contains("q=cart");
        assertThat(lastAuth.get()).isEqualTo("token forgejo-token");
        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().externalId()).isEqualTo("acme/webshop#7");
        assertThat(issues.getFirst().state()).isEqualTo(IssueState.OPEN);
        assertThat(issues.getFirst().title()).isEqualTo("Cart empties");
    }

    @Test
    void search_asksForIssuesOnlyNotPullRequests() {
        responseBody.set("[]");

        provider.search(config, "cart");

        // Without this the endpoint returns pull requests too, which must never be linkable as defects.
        assertThat(lastQuery.get()).contains("type=issues");
    }

    @Test
    void search_skipsAnythingCarryingAPullRequestPayload() {
        responseBody.set("""
                [
                  {"number": 7, "title": "Real issue", "state": "open", "html_url": "https://forge.test/i/7"},
                  {"number": 8, "title": "A merge request", "state": "open", "html_url": "https://forge.test/p/8",
                   "pull_request": {"merged": false}}
                ]
                """);

        List<Issue> issues = provider.search(config, "x");

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().externalId()).isEqualTo("acme/webshop#7");
    }

    @Test
    void create_postsTitleAndBody() {
        responseBody.set("""
                {"number": 12, "title": "New defect", "state": "open", "html_url": "https://forge.test/i/12"}
                """);
        responseCode.set(201);

        Issue issue = provider.create(config, new IssueDraft("New defect", "Body text"));

        assertThat(lastMethod.get()).isEqualTo("POST");
        // Forgejo names the description field "body", where GitLab calls it "description".
        assertThat(lastRequestBody.get()).contains("\"title\":\"New defect\"");
        assertThat(lastRequestBody.get()).contains("\"body\":\"Body text\"");
        assertThat(issue.externalId()).isEqualTo("acme/webshop#12");
        assertThat(issue.url()).isEqualTo("https://forge.test/i/12");
    }

    @Test
    void get_stripsTheRepoPrefixToReachTheIndex() {
        responseBody.set("""
                {"number": 7, "title": "Cart empties", "state": "closed", "html_url": "https://forge.test/i/7"}
                """);

        Issue issue = provider.get(config, "acme/webshop#7");

        assertThat(lastPath.get()).isEqualTo("/api/v1/repos/acme/webshop/issues/7");
        assertThat(issue.state()).isEqualTo(IssueState.CLOSED);
    }

    @Test
    void get_rejectsAReferenceThatIsNotAnIndex() {
        assertThatThrownBy(() -> provider.get(config, "acme/webshop#not-a-number"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid Forgejo issue reference");
    }

    @Test
    void unknownStateBecomesUnknownRatherThanGuessing() {
        responseBody.set("""
                {"number": 3, "title": "Odd", "state": "archived", "html_url": "https://forge.test/i/3"}
                """);

        assertThat(provider.get(config, "acme/webshop#3").state()).isEqualTo(IssueState.UNKNOWN);
    }

    @Test
    void rejectsAProjectRefThatIsNotOwnerSlashRepo() {
        assertThatThrownBy(() -> provider.search(configFor("justarepo"), "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner/repository");

        // A GitLab-style nested group path is not addressable through this API shape, so it is
        // rejected up front rather than producing a confusing 404 later.
        assertThatThrownBy(() -> provider.search(configFor("group/sub/project"), "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner/repository");
    }

    @Test
    void encodesOwnerAndRepoSeparately() {
        responseBody.set("[]");

        provider.search(configFor("my org/web shop"), "x");

        // %20, not + — a plus in a path segment is a literal plus, not a space.
        assertThat(lastPath.get()).isEqualTo("/api/v1/repos/my%20org/web%20shop/issues");
    }

    @Test
    void authFailureIsReportedAsAnUpstreamError() {
        responseCode.set(401);

        assertThatThrownBy(() -> provider.search(config, "x"))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("Forgejo rejected the configured access token");
    }

    @Test
    void missingRepoIsDistinguishedFromAuthFailure() {
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
        responseBody.set("<html>gateway error</html>");

        assertThatThrownBy(() -> provider.search(config, "x"))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("malformed response");
    }

    @Test
    void unreachableHostIsReportedWithoutTheToken() {
        server.stop(0);

        assertThatThrownBy(() -> provider.search(config, "x"))
                .isInstanceOf(UpstreamServiceException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("forgejo-token"));
    }

    @Test
    void testConnection_hitsTheRepoEndpoint() {
        responseBody.set("{\"id\": 1, \"full_name\": \"acme/webshop\"}");

        provider.testConnection(config);

        assertThat(lastPath.get()).isEqualTo("/api/v1/repos/acme/webshop");
    }
}
