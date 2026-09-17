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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the Jira adapter against a local stub (PRD-029 §3.2). Cloud and Data Center are told
 * apart by the host, which a stub on 127.0.0.1 cannot imitate, so the detector is injected: each
 * test says which flavour the stub is playing.
 */
class JiraIssueProviderTest {

    private static final String SEARCH_RESULT = """
            {"issues": [{"key": "WEB-7", "fields": {"summary": "Cart empties",
              "status": {"name": "In Arbeit", "statusCategory": {"key": "indeterminate"}}}}]}
            """;

    private HttpServer server;
    private final AtomicInteger responseCode = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("{}");
    private final AtomicReference<String> responseContentType = new AtomicReference<>("application/json");
    private final List<String> requestedPaths = new ArrayList<>();
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestedPaths.add(exchange.getRequestURI().getRawPath());
            lastQuery.set(exchange.getRequestURI().getQuery());
            lastMethod.set(exchange.getRequestMethod());
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", responseContentType.get());
            exchange.sendResponseHeaders(responseCode.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private JiraIssueProvider provider(boolean cloud) {
        IssueTrackerProperties properties = new IssueTrackerProperties(
                null, true, false, 2000, 3000, 3600000L, 50, 0L);
        return new JiraIssueProvider(properties, new ObjectMapper(), baseUrl -> cloud);
    }

    private IssueTrackerProvider.DecryptedConfig config(String projectRef) {
        IssueTrackerConfig entity = new IssueTrackerConfig();
        entity.setProvider(IssueTrackerProviderType.JIRA);
        entity.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        entity.setProjectRef(projectRef);
        entity.setAuthUsername("qa@example.com");
        return new IssueTrackerProvider.DecryptedConfig(entity, "jira-token");
    }

    private String lastPath() {
        return requestedPaths.getLast();
    }

    /** The query as the server reads it: URI.getQuery() decodes %XX but leaves form-encoded '+' alone. */
    private String decodedQuery() {
        return lastQuery.get().replace('+', ' ');
    }

    // ---- flavours -----------------------------------------------------------------------

    @Test
    void cloudAuthenticatesWithTheAccountEmailAndTokenAsBasic() {
        responseBody.set(SEARCH_RESULT);

        provider(true).search(config("WEB"), "cart");

        String expected = Base64.getEncoder().encodeToString("qa@example.com:jira-token".getBytes(StandardCharsets.UTF_8));
        assertThat(lastAuth.get()).isEqualTo("Basic " + expected);
    }

    @Test
    void dataCenterAuthenticatesWithThePersonalAccessTokenAsBearer() {
        responseBody.set(SEARCH_RESULT);

        provider(false).search(config("WEB"), "cart");

        assertThat(lastAuth.get()).isEqualTo("Bearer jira-token");
    }

    @Test
    void cloudSearchesOnTheNewJqlEndpoint() {
        responseBody.set(SEARCH_RESULT);

        provider(true).search(config("WEB"), "cart");

        // The legacy /search was removed from Cloud in 2025.
        assertThat(lastPath()).isEqualTo("/rest/api/3/search/jql");
    }

    @Test
    void dataCenterSearchesOnTheClassicEndpoint() {
        responseBody.set(SEARCH_RESULT);

        provider(false).search(config("WEB"), "cart");

        assertThat(lastPath()).isEqualTo("/rest/api/2/search");
    }

    @Test
    void onlyAnAtlassianNetHostIsCloud() {
        assertThat(JiraIssueProvider.isAtlassianCloud("https://acme.atlassian.net")).isTrue();
        assertThat(JiraIssueProvider.isAtlassianCloud("https://acme.atlassian.net/")).isTrue();
        assertThat(JiraIssueProvider.isAtlassianCloud("https://jira.acme.example")).isFalse();
        // A look-alike host must not be sent the account email and token as Basic auth.
        assertThat(JiraIssueProvider.isAtlassianCloud("https://atlassian.net.evil.example")).isFalse();
    }

    // ---- search -------------------------------------------------------------------------

    @Test
    void search_isScopedToTheProjectAndMapsTheStatusCategoryNotTheStatusName() {
        responseBody.set(SEARCH_RESULT);

        List<Issue> issues = provider(false).search(config("WEB"), "cart");

        assertThat(decodedQuery()).contains("project = \"WEB\"").contains("text ~ \"cart\"")
                .contains("fields=summary,status").contains("maxResults=20");
        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.externalId()).isEqualTo("WEB-7");
            assertThat(issue.title()).isEqualTo("Cart empties");
            // "In Arbeit" is this workflow's name for it; the category is what Jira fixes.
            assertThat(issue.state()).isEqualTo(IssueState.OPEN);
            assertThat(issue.url()).endsWith("/browse/WEB-7");
        });
    }

    @Test
    void search_escapesQuotesAndBackslashesSoTheTextCannotRewriteTheQuery() {
        responseBody.set("{\"issues\": []}");

        provider(false).search(config("WEB"), "a\" OR project = \"SECRET\\");

        assertThat(decodedQuery()).contains("text ~ \"a\\\" OR project = \\\"SECRET\\\\\"");
    }

    @Test
    void search_forAnIssueKeyFetchesItDirectly() {
        responseBody.set("""
                {"key": "WEB-12", "fields": {"summary": "Login loops", "status": {"statusCategory": {"key": "done"}}}}
                """);

        List<Issue> issues = provider(false).search(config("WEB"), "web-12");

        // JQL naming a key that does not exist is a 400 in Jira, so a key is looked up, not searched.
        assertThat(requestedPaths).containsExactly("/rest/api/2/issue/WEB-12");
        assertThat(issues).singleElement().satisfies(issue -> assertThat(issue.state()).isEqualTo(IssueState.CLOSED));
    }

    @Test
    void search_forAKeyThatDoesNotExistFallsBackToATextSearch() {
        responseCode.set(404);

        assertThatThrownBy(() -> provider(false).search(config("WEB"), "WEB-999"))
                .isInstanceOf(UpstreamServiceException.class);
        // Looked up first, then searched — the stub answers 404 to both, which ends the attempt.
        assertThat(requestedPaths).containsExactly("/rest/api/2/issue/WEB-999", "/rest/api/2/search");
    }

    // ---- create -------------------------------------------------------------------------

    @Test
    void create_sendsWikiMarkupAndBuildsTheIssueWithoutASecondRequest() {
        responseCode.set(201);
        responseBody.set("{\"id\": \"10042\", \"key\": \"WEB-42\", \"self\": \"x\"}");

        Issue issue = provider(true).create(config("WEB"), new IssueDraft("Checkout 500", "**Result:** FAILED"));

        assertThat(requestedPaths).containsExactly("/rest/api/2/issue");
        assertThat(lastMethod.get()).isEqualTo("POST");
        assertThat(lastRequestBody.get())
                .contains("\"project\":{\"key\":\"WEB\"}")
                .contains("\"issuetype\":{\"name\":\"Bug\"}")
                .contains("\"summary\":\"Checkout 500\"")
                // Jira does not render Markdown; v2 still takes wiki markup, which avoids building ADF.
                .contains("*Result:* FAILED").doesNotContain("**Result:**");
        assertThat(issue.externalId()).isEqualTo("WEB-42");
        assertThat(issue.title()).isEqualTo("Checkout 500");
        assertThat(issue.state()).isEqualTo(IssueState.OPEN);
        assertThat(issue.url()).endsWith("/browse/WEB-42");
    }

    @Test
    void create_usesTheIssueTypeNamedInTheProjectReference() {
        responseCode.set(201);
        responseBody.set("{\"key\": \"WEB-43\"}");

        provider(false).create(config("WEB:Task"), new IssueDraft("t", "b"));

        assertThat(lastRequestBody.get()).contains("\"project\":{\"key\":\"WEB\"}").contains("\"issuetype\":{\"name\":\"Task\"}");
    }

    @Test
    void create_namesTheFieldsJiraInsistsOnInsteadOfABareHttp400() {
        responseCode.set(400);
        responseBody.set("""
                {"errorMessages": [], "errors": {"components": "Component/s is required.", "fixVersions": "Fix Version/s is required."}}
                """);

        assertThatThrownBy(() -> provider(false).create(config("WEB"), new IssueDraft("t", "b")))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("Jira requires")
                .hasMessageContaining("components").hasMessageContaining("fixVersions")
                .hasMessageContaining("link an issue created in Jira instead");
    }

    // ---- get ----------------------------------------------------------------------------

    @Test
    void get_reportsTheKeyJiraReturnsSoAMovedIssueIsFollowed() {
        responseBody.set("""
                {"key": "SHOP-3", "fields": {"summary": "Moved", "status": {"statusCategory": {"key": "new"}}}}
                """);

        Issue issue = provider(false).get(config("WEB"), "WEB-12");

        assertThat(issue.externalId()).isEqualTo("SHOP-3");
        assertThat(issue.url()).endsWith("/browse/SHOP-3");
    }

    @Test
    void anUnfamiliarStatusCategoryIsUnknownRatherThanGuessed() {
        responseBody.set("""
                {"key": "WEB-1", "fields": {"summary": "x", "status": {"statusCategory": {"key": "undefined"}}}}
                """);

        assertThat(provider(false).get(config("WEB"), "WEB-1").state()).isEqualTo(IssueState.UNKNOWN);
    }

    // ---- test connection ----------------------------------------------------------------

    @Test
    void testConnection_failsWhenTheProjectHasNoSuchIssueType() {
        responseBody.set("{\"key\": \"WEB\", \"issueTypes\": [{\"name\": \"Task\"}, {\"name\": \"Story\"}]}");

        // Otherwise "Bug doesn't exist here" first shows up when someone files from a failure.
        assertThatThrownBy(() -> provider(false).testConnection(config("WEB")))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("no issue type 'Bug'")
                .hasMessageContaining("Task, Story");
    }

    @Test
    void testConnection_passesWhenTheIssueTypeExistsWhateverItsCase() {
        responseBody.set("{\"key\": \"WEB\", \"issueTypes\": [{\"name\": \"bug\"}]}");

        provider(false).testConnection(config("WEB"));

        assertThat(lastPath()).isEqualTo("/rest/api/2/project/WEB");
    }

    // ---- failures -----------------------------------------------------------------------

    @Test
    void aLoginPageServedWith200IsARejectedTokenNotAMalformedResponse() {
        responseContentType.set("text/html;charset=UTF-8");
        responseBody.set("<html><body>Log in to Jira</body></html>");

        assertThatThrownBy(() -> provider(false).testConnection(config("WEB")))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("rejected the configured access token");
    }

    @Test
    void cloudWithoutAnAccountEmailSaysWhatIsMissing() {
        IssueTrackerProvider.DecryptedConfig config = config("WEB");
        config.config().setAuthUsername(null);

        assertThatThrownBy(() -> provider(true).testConnection(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("account email");
    }

    @Test
    void aProjectReferenceWithAnEmptyKeyIsTheCallersMistake() {
        assertThatThrownBy(() -> provider(false).testConnection(config(":Task")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("project key");
    }
}
