package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.apiKey.ApiKeyResponse;
import com.deanmanagement.testmanagement.project.internal.dto.apiKey.CreateApiKeyRequest;
import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService;
import com.deanmanagement.testmanagement.project.internal.service.ProjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PRD-027 §9: a key restricted to some tool groups sees, and can call, only those. Over real HTTP,
 * because both halves live outside the tool classes — the list is trimmed by a servlet filter and
 * the call is refused by the audit aspect.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "app.mcp.enabled=true")
class McpToolGroupsApiTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOOLS_LIST = """
            {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
            """;

    @LocalServerPort
    private int port;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private McpToolGroups toolGroups;
    @Autowired
    private McpToolInvocationRepository invocationRepository;

    private Project project;

    @BeforeEach
    void setUp() {
        Project p = new Project();
        p.setName("Tool groups");
        p.setKey("G" + Integer.toHexString(new java.util.Random().nextInt(0xFFFFF)));
        project = projectRepository.save(p);
    }

    @AfterEach
    void tearDown() {
        projectService.delete(project.getId(), null);
    }

    private String keyWith(Set<McpToolGroup> groups) {
        return apiKeyService.create(new CreateApiKeyRequest("agent-" + UUID.randomUUID(),
                project.getId(), ProjectRole.TESTER, groups)).rawKey();
    }

    // --- the catalogue ---------------------------------------------------------------------

    /**
     * The guard against the quiet failure: a new tool class without {@code @InToolGroup} would be
     * invisible to every restricted key, and nothing else would say so.
     */
    @Test
    void everyAdvertisedToolBelongsToAGroup() throws Exception {
        List<String> advertised = toolNames(keyWith(null));

        assertThat(advertised).isNotEmpty();
        assertThat(advertised).allSatisfy(name ->
                assertThat(toolGroups.groupOf(name)).as("tool group of %s", name).isPresent());
    }

    // --- the list --------------------------------------------------------------------------

    @Test
    void aRestrictedKeyIsShownOnlyItsGroupsAndTheCoreTool() throws Exception {
        List<String> shown = toolNames(keyWith(EnumSet.of(McpToolGroup.EXECUTION)));

        assertThat(shown).contains("get_project", "create_test_run", "record_test_result");
        assertThat(shown).doesNotContain("create_test_case", "trigger_pipeline",
                "create_linked_issue", "get_project_dashboard");
        assertThat(shown).allSatisfy(name -> assertThat(toolGroups.groupOf(name)).get()
                .isIn(McpToolGroup.CORE, McpToolGroup.EXECUTION));
    }

    /** Traceability lives in the requirements class but is a report, so it is placed per method. */
    @Test
    void aReportingKeyCanReadTraceabilityButNotEditRequirements() throws Exception {
        List<String> shown = toolNames(keyWith(EnumSet.of(McpToolGroup.REPORTING)));

        assertThat(shown).contains("get_traceability_matrix", "get_project_dashboard");
        assertThat(shown).doesNotContain("create_requirement", "list_requirements");
    }

    @Test
    void anUnrestrictedKeyIsShownEverything() throws Exception {
        assertThat(toolNames(keyWith(null)))
                .contains("create_test_case", "create_test_run", "trigger_pipeline",
                        "create_linked_issue", "get_project_dashboard");
    }

    @Test
    void trimmingLeavesAValidJsonRpcResponse() throws Exception {
        JsonNode response = MAPPER.readTree(
                post(TOOLS_LIST, keyWith(EnumSet.of(McpToolGroup.REPORTING))).body());

        assertThat(response.path("jsonrpc").asString()).isEqualTo("2.0");
        assertThat(response.path("id").asInt()).isEqualTo(2);
        assertThat(response.path("result").path("tools").get(0).path("inputSchema").isObject())
                .isTrue();
    }

    // --- the call --------------------------------------------------------------------------

    /** The list is a courtesy; this is the boundary. A client can call a tool it was never shown. */
    @Test
    void callingAToolOutsideTheKeysGroupsIsRefusedAndAudited() throws Exception {
        String rawKey = keyWith(EnumSet.of(McpToolGroup.EXECUTION));

        JsonNode result = MAPPER.readTree(post("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"create_test_case",
                 "arguments":{"title":"Should never exist","priority":"LOW"}}}
                """, rawKey).body()).path("result");

        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(result.path("content").get(0).path("text").asString())
                .contains("not allowed to use create_test_case")
                .contains("AUTHORING");
        assertThat(invocationRepository.findAll())
                .filteredOn(row -> "create_test_case".equals(row.getToolName())
                        && "REFUSED".equals(row.getOutcome()))
                .isNotEmpty();
    }

    @Test
    void aToolInsideTheKeysGroupsStillWorks() throws Exception {
        String rawKey = keyWith(EnumSet.of(McpToolGroup.EXECUTION));

        JsonNode result = MAPPER.readTree(post("""
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"get_project",
                 "arguments":{}}}
                """, rawKey).body()).path("result");

        assertThat(result.path("isError").asBoolean(false)).isFalse();
        assertThat(result.path("structuredContent").path("key").asString())
                .isEqualTo(project.getKey());
    }

    // --- issuing keys ----------------------------------------------------------------------

    @Test
    void selectingEveryGroupIsStoredAsUnrestricted() {
        UUID keyId = apiKeyService.create(new CreateApiKeyRequest("all-" + UUID.randomUUID(),
                project.getId(), ProjectRole.TESTER,
                EnumSet.complementOf(EnumSet.of(McpToolGroup.CORE)))).id();

        assertThat(listed(keyId).mcpToolGroups()).isNull();
    }

    @Test
    void aRestrictedKeyReportsItsGroups() {
        UUID keyId = apiKeyService.create(new CreateApiKeyRequest("some-" + UUID.randomUUID(),
                project.getId(), ProjectRole.TESTER,
                EnumSet.of(McpToolGroup.AUTHORING, McpToolGroup.REPORTING))).id();

        assertThat(listed(keyId).mcpToolGroups())
                .containsExactlyInAnyOrder(McpToolGroup.AUTHORING, McpToolGroup.REPORTING);
    }

    @Test
    void coreCannotBeSelectedBecauseItIsAlwaysGranted() {
        assertThatThrownBy(() -> keyWith(EnumSet.of(McpToolGroup.CORE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CORE");
    }

    private ApiKeyResponse listed(UUID keyId) {
        return apiKeyService.findAll().stream()
                .filter(key -> key.id().equals(keyId)).findFirst().orElseThrow();
    }

    private List<String> toolNames(String rawKey) throws Exception {
        List<String> names = new ArrayList<>();
        MAPPER.readTree(post(TOOLS_LIST, rawKey).body()).path("result").path("tools")
                .forEach(tool -> names.add(tool.path("name").asString()));
        return names;
    }

    private HttpResponse<String> post(String body, String rawKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/mcp"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("Authorization", "Bearer " + rawKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
