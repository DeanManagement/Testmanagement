package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.apiKey.CreateApiKeyRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Whether someone holding a key can find their way in.
 *
 * <p>Every case here is drawn from an agent that failed on a live instance. It had a valid key,
 * was told only "there is an MCP server at this host", and got: a bare 403 from {@code /api/*}
 * that read as "wrong credential", a silent 405 from {@code GET /api/mcp}, and a 200 of HTML from
 * {@code /.well-known/}. It concluded the product was TM4J, went looking for a login endpoint, and
 * asked its user for a password. Nothing it saw was wrong, and nothing it saw was useful.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.mcp.enabled=true")
class McpDiscoverabilityApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private ProjectRepository projectRepository;

    private static final String KEY = "tm_somethingthatlookslikeakey";

    private static final String TOOLS_LIST =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}";

    private UUID revocableProjectId;

    @BeforeEach
    void createProjectForKeys() {
        Project project = new Project();
        project.setName("Discoverability");
        project.setKey("DISC" + Integer.toHexString(new java.util.Random().nextInt(0xFFFF)));
        revocableProjectId = projectRepository.save(project).getId();
    }

    @Test
    void anApiKeyOnTheWrongPathIsToldWhereItWorks() throws Exception {
        mockMvc.perform(get("/api/projects").header("X-API-Key", KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.hint").value(org.hamcrest.Matchers.containsString("/api/mcp")))
                .andExpect(jsonPath("$.hint").value(org.hamcrest.Matchers.containsString("/api/external")));
    }

    @Test
    void theSameHelpReachesABearerStyleCaller() throws Exception {
        // MCP clients only send Authorization: Bearer, so the hint has to cover that form too.
        mockMvc.perform(get("/api/projects").header("Authorization", "Bearer " + KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mcpEndpoint").value("/api/mcp"));
    }

    @Test
    void aRequestWithoutAnyKeyGetsTheOrdinaryRejection() throws Exception {
        // The hint is for someone holding a credential, not an advertisement to every caller.
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));
    }

    @Test
    void aJwtBearerIsNotMistakenForAnApiKey() throws Exception {
        // A malformed session token is still handled by the JWT filter, which answers with its own
        // 401. What matters is that it is not sent to the API-key surface, which would be wrong
        // advice for someone whose session has simply expired.
        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.sig"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("mcpEndpoint"))));
    }

    @Test
    void getOnTheMcpEndpointDescribesHowToConnect() throws Exception {
        // Unauthenticated on purpose: the reader is someone who cannot authenticate yet.
        mockMvc.perform(get("/api/mcp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpoint").value("/api/mcp"))
                .andExpect(jsonPath("$.transport").value(org.hamcrest.Matchers.containsString("streamable-http")))
                .andExpect(jsonPath("$.authentication[0]").value(org.hamcrest.Matchers.containsString("Bearer")))
                .andExpect(jsonPath("$.documentation").value(org.hamcrest.Matchers.containsString("MCP_SETUP")));
    }

    /**
     * The descriptor's other reader is an agent orienting itself, and it used to lead with a
     * ready-to-paste curl command — a working recipe for the worst way to use the server. A model
     * that fetched this URL copied it, and then drove JSON-RPC by hand for the rest of the session
     * without the tool schemas or its client's tool permissions.
     *
     * <p>So: the client configuration a caller should actually use has to be present and machine-
     * readable, and the manual path has to be labelled as the debugging aid it is.
     */
    @Test
    void theDescriptorTellsAnAgentToRegisterTheServerRatherThanDriveItByHand() throws Exception {
        mockMvc.perform(get("/api/mcp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.howToUse").value(org.hamcrest.Matchers.containsString("tool list")))
                .andExpect(jsonPath("$.clientConfiguration.mcpServers.testmanagement.url")
                        .value(org.hamcrest.Matchers.containsString("/api/mcp")))
                .andExpect(jsonPath("$.clientConfiguration.mcpServers.testmanagement.headers.Authorization")
                        .value(org.hamcrest.Matchers.containsString("Bearer")))
                // The curl survives — humans and CI need it — but under a name that says who it is
                // for, and after the thing an agent should read first.
                .andExpect(jsonPath("$.manualExample").value(org.hamcrest.Matchers.containsString("curl")))
                .andExpect(jsonPath("$.manualExample").value(org.hamcrest.Matchers.containsString("debugging")))
                .andExpect(jsonPath("$.example").doesNotExist());
    }

    /**
     * "Invalid or revoked API key" covered both cases and helped with neither. The next move is
     * different for each — ask for a new key, versus check the copy you are holding — and a session
     * reported spending several round trips re-probing the auth header off the back of it, when the
     * header was never the problem (PRD-027 §8.3).
     */
    @Test
    void anUnknownKeyAndARevokedKeyAreDistinguished() throws Exception {
        // Against /api/mcp, where the key filter actually runs — /api/projects is answered by the
        // signpost entry point instead, which is a different message for a different problem.
        mockMvc.perform(post("/api/mcp").header("X-API-Key", KEY)
                        .contentType("application/json")
                        .accept("application/json", "text/event-stream")
                        .content(TOOLS_LIST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error")
                        .value(org.hamcrest.Matchers.containsString("No API key matches")));

        String live = apiKeyService.create(
                new CreateApiKeyRequest("to-be-revoked", revocableProjectId, ProjectRole.TESTER))
                .rawKey();
        apiKeyService.revoke(apiKeyService.findAll().stream()
                .filter(k -> "to-be-revoked".equals(k.name()))
                .findFirst().orElseThrow().id());

        mockMvc.perform(post("/api/mcp").header("X-API-Key", live)
                        .contentType("application/json")
                        .accept("application/json", "text/event-stream")
                        .content(TOOLS_LIST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error")
                        .value(org.hamcrest.Matchers.containsString("revoked")));
    }

    @Test
    void wellKnownDoesNotAnswerWithTheAppShell() throws Exception {
        // A 200 of HTML tells a machine the discovery document exists. A 404 is the truth.
        mockMvc.perform(get("/.well-known/mcp"))
                .andExpect(status().isNotFound());
    }
}
