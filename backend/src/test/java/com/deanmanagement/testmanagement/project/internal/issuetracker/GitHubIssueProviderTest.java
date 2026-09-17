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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the GitHub adapter against a local stub (PRD-029 §3.3). The stub's address is not
 * github.com, so every request here takes the GitHub Enterprise Server shape, {@code /api/v3};
 * the github.com mapping is covered on its own at the bottom.
 */
class GitHubIssueProviderTest {

    private HttpServer server;
    private GitHubIssueProvider provider;
    private IssueTrackerProvider.DecryptedConfig config;

    private final AtomicInteger responseCode = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("{}");
    private final Map<String, String> responseHeaders = new LinkedHashMap<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private final Map<String, String> lastHeaders = new LinkedHashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().getRawPath());
            lastQuery.set(exchange.getRequestURI().getQuery());
            lastMethod.set(exchange.getRequestMethod());
            lastHeaders.clear();
            exchange.getRequestHeaders().forEach((name, values) -> lastHeaders.put(name.toLowerCase(), values.getFirst()));
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            responseHeaders.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
            exchange.sendResponseHeaders(responseCode.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        IssueTrackerProperties properties = new IssueTrackerProperties(
                null, true, false, 2000, 3000, 3600000L, 50, 0L);
        provider = new GitHubIssueProvider(properties, new ObjectMapper());
        config = configFor("acme/webshop");
    }

    private IssueTrackerProvider.DecryptedConfig configFor(String projectRef) {
        IssueTrackerConfig entity = new IssueTrackerConfig();
        entity.setProvider(IssueTrackerProviderType.GITHUB);
        entity.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        entity.setProjectRef(projectRef);
        return new IssueTrackerProvider.DecryptedConfig(entity, "gh-token");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    // ---- requests -----------------------------------------------------------------------

    @Test
    void everyRequestCarriesTheHeadersGitHubInsistsOn() {
        responseBody.set("{\"has_issues\": true}");

        provider.testConnection(config);

        assertThat(lastHeaders.get("authorization")).isEqualTo("Bearer gh-token");
        assertThat(lastHeaders.get("accept")).isEqualTo("application/vnd.github+json");
        assertThat(lastHeaders.get("x-github-api-version")).isEqualTo("2022-11-28");
        // GitHub rejects requests without a User-Agent, and default client UAs get blocked by CDNs.
        assertThat(lastHeaders.get("user-agent")).isEqualTo("Testmanagement");
    }

    @Test
    void search_isScopedToTheRepositoryAndToIssues() {
        responseBody.set("""
                {"total_count": 1, "items": [
                  {"number": 7, "title": "Cart empties", "state": "open", "html_url": "https://github.test/acme/webshop/issues/7"}]}
                """);

        List<Issue> issues = provider.search(config, "cart empties");

        assertThat(lastPath.get()).isEqualTo("/api/v3/search/issues");
        // is:issue excludes pull requests server-side; repo: keeps another repository's issues out.
        // Form-encoded, so the spaces between the qualifiers arrive as '+'.
        assertThat(lastQuery.get()).contains("q=repo:acme/webshop+is:issue+cart+empties");
        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.externalId()).isEqualTo("acme/webshop#7");
            assertThat(issue.state()).isEqualTo(IssueState.OPEN);
            assertThat(issue.url()).isEqualTo("https://github.test/acme/webshop/issues/7");
        });
    }

    @Test
    void search_forANumberFetchesThatIssueDirectly() {
        responseBody.set("{\"number\": 123, \"title\": \"Login loops\", \"state\": \"closed\", \"html_url\": \"u\"}");

        List<Issue> issues = provider.search(config, "#123");

        // The search API allows 30 requests a minute; a number needs no search at all.
        assertThat(lastPath.get()).isEqualTo("/api/v3/repos/acme/webshop/issues/123");
        assertThat(issues).singleElement().satisfies(issue -> assertThat(issue.state()).isEqualTo(IssueState.CLOSED));
    }

    @Test
    void search_forANumberThatDoesNotExistFindsNothingRatherThanFailing() {
        responseCode.set(404);

        assertThat(provider.search(config, "999")).isEmpty();
    }

    @Test
    void create_postsTitleAndMarkdownBody() {
        responseCode.set(201);
        responseBody.set("{\"number\": 8, \"title\": \"Checkout 500\", \"state\": \"open\", \"html_url\": \"u\"}");

        Issue issue = provider.create(config, new IssueDraft("Checkout 500", "**Steps**\n1. pay"));

        assertThat(lastMethod.get()).isEqualTo("POST");
        assertThat(lastPath.get()).isEqualTo("/api/v3/repos/acme/webshop/issues");
        assertThat(lastRequestBody.get()).contains("\"title\":\"Checkout 500\"").contains("**Steps**");
        assertThat(issue.externalId()).isEqualTo("acme/webshop#8");
    }

    // ---- pull requests ------------------------------------------------------------------

    @Test
    void get_refusesAPullRequestBecauseTheIssuesEndpointReturnsThoseToo() {
        responseBody.set("{\"number\": 9, \"title\": \"Refactor\", \"state\": \"open\", \"pull_request\": {\"url\": \"x\"}}");

        assertThatThrownBy(() -> provider.get(config, "acme/webshop#9"))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("#9 is a pull request, not an issue");
    }

    @Test
    void closedAsNotPlannedIsStillClosed() {
        responseBody.set("{\"number\": 4, \"title\": \"Won't fix\", \"state\": \"closed\", \"state_reason\": \"not_planned\", \"html_url\": \"u\"}");

        assertThat(provider.get(config, "acme/webshop#4").state()).isEqualTo(IssueState.CLOSED);
    }

    // ---- test connection ----------------------------------------------------------------

    @Test
    void testConnection_failsForARepositoryWithIssuesSwitchedOff() {
        responseBody.set("{\"has_issues\": false}");

        // Common on forks; otherwise it would only surface as a 410 at the first filing.
        assertThatThrownBy(() -> provider.testConnection(config))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("issues disabled");
    }

    // ---- failures -----------------------------------------------------------------------

    @Test
    void aSecondaryRateLimitIsNotReportedAsARejectedToken() {
        responseCode.set(403);
        responseHeaders.put("x-ratelimit-remaining", "0");
        responseBody.set("{\"message\": \"API rate limit exceeded\"}");

        assertThatThrownBy(() -> provider.search(config, "cart"))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("rate limit")
                .hasMessageNotContaining("rejected");
    }

    @Test
    void a403WithRetryAfterIsARateLimitToo() {
        responseCode.set(403);
        responseHeaders.put("retry-after", "60");

        assertThatThrownBy(() -> provider.search(config, "cart"))
                .hasMessageContaining("rate limit");
    }

    @Test
    void aPlain403IsStillARejectedToken() {
        responseCode.set(403);

        assertThatThrownBy(() -> provider.search(config, "cart"))
                .hasMessageContaining("rejected the configured access token");
    }

    @Test
    void goneMeansIssuesWereDisabledAfterConfiguration() {
        responseCode.set(410);

        assertThatThrownBy(() -> provider.create(config, new IssueDraft("t", "b")))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("issues disabled");
    }

    @Test
    void aProjectReferenceThatIsNotOwnerSlashRepoIsTheCallersMistake() {
        assertThatThrownBy(() -> provider.testConnection(configFor("just-a-repo")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner/repository");
    }

    // ---- api base -----------------------------------------------------------------------

    @Test
    void githubDotComIsServedFromItsApiHost() {
        assertThat(GitHubIssueProvider.apiBase("https://github.com")).isEqualTo("https://api.github.com");
        assertThat(GitHubIssueProvider.apiBase("https://github.com/")).isEqualTo("https://api.github.com");
        assertThat(GitHubIssueProvider.apiBase("https://www.github.com")).isEqualTo("https://api.github.com");
    }

    @Test
    void anyOtherHostIsAnEnterpriseServer() {
        assertThat(GitHubIssueProvider.apiBase("https://git.corp.example/")).isEqualTo("https://git.corp.example/api/v3");
    }
}
