package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.apiKey.CreateApiKeyRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD-025 §3.1/§3.3. The transport, over real HTTP — MockMvc would not exercise the security chain
 * ordering, which is where the interesting failure modes live.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "app.mcp.enabled=true")
class McpEndpointApiTest {

    @LocalServerPort
    private int port;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ApiKeyService apiKeyService;

    private String rawKey;

    private static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
              "protocolVersion":"2025-06-18",
              "capabilities":{},
              "clientInfo":{"name":"test-client","version":"1.0"}}}
            """;

    private static final String TOOLS_LIST = """
            {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
            """;

    @BeforeEach
    void setUp() {
        Project project = projectRepository.findByKey("MCPE").orElseGet(() -> {
            Project p = new Project();
            p.setKey("MCPE");
            p.setName("MCP Endpoint Test");
            return projectRepository.save(p);
        });
        rawKey = apiKeyService.create(
                new CreateApiKeyRequest("mcp-endpoint-probe", project.getId(), ProjectRole.TESTER))
                .rawKey();
    }

    @Test
    void toolsAreAdvertisedOverTheProtocol() throws Exception {
        HttpResponse<String> initialize = post(INITIALIZE, "X-API-Key", rawKey);
        assertThat(initialize.statusCode()).isEqualTo(200);

        HttpResponse<String> tools = post(TOOLS_LIST, "X-API-Key", rawKey);

        assertThat(tools.statusCode()).isEqualTo(200);
        // The whole authoring surface, by name — a tool silently failing to register is the
        // failure this catches, and it is invisible from the Java side.
        assertThat(tools.body())
                .contains("get_project")
                .contains("search_test_cases")
                .contains("get_test_case")
                .contains("list_test_case_folders")
                .contains("list_test_suites")
                .contains("get_test_suite")
                .contains("list_test_plans")
                .contains("get_test_plan")
                .contains("create_test_case")
                .contains("update_test_case")
                .contains("create_test_cases_bulk")
                .contains("create_test_suite")
                .contains("create_test_plan");
        // Schemas are derived from the method signatures, so an enum an agent must get right
        // should appear in them rather than only in prose.
        assertThat(tools.body()).contains("CRITICAL").contains("DEPRECATED");
    }

    @Test
    void execution_toolsAreNotExposed() throws Exception {
        // v1 is authoring only (§2 non-goals). Recording results stays PRD-005's job, and no tool
        // deletes anything.
        HttpResponse<String> tools = post(TOOLS_LIST, "X-API-Key", rawKey);

        assertThat(tools.body())
                .doesNotContain("create_test_run")
                .doesNotContain("record_result")
                .doesNotContain("delete_test_case");
    }

    @Test
    void bearerTokenIsAcceptedSoStandardMcpClientsWork() throws Exception {
        // MCP clients only know how to send a bearer token; X-API-Key is not in their vocabulary.
        HttpResponse<String> response = post(INITIALIZE, "Authorization", "Bearer " + rawKey);

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void missingKeyIsChallengedNotJustRejected() throws Exception {
        HttpResponse<String> response = post(INITIALIZE, null, null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).isPresent();
    }

    @Test
    void aJwtShapedBearerTokenIsRejected() throws Exception {
        // The tm_ prefix is what separates a key from a session token; without that check,
        // widening the header would have let JWTs in through the API-key chain.
        HttpResponse<String> response = post(INITIALIZE, "Authorization",
                "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.signature");

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void invalidKeyIsRejected() throws Exception {
        HttpResponse<String> response = post(INITIALIZE, "X-API-Key", "tm_notarealkey");

        assertThat(response.statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> post(String body, String headerName, String headerValue)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/mcp"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (headerName != null) {
            request.header(headerName, headerValue);
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
