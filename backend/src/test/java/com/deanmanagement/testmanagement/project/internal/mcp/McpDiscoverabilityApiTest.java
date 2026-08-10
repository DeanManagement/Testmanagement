package com.deanmanagement.testmanagement.project.internal.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private static final String KEY = "tm_somethingthatlookslikeakey";

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

    @Test
    void wellKnownDoesNotAnswerWithTheAppShell() throws Exception {
        // A 200 of HTML tells a machine the discovery document exists. A 404 is the truth.
        mockMvc.perform(get("/.well-known/mcp"))
                .andExpect(status().isNotFound());
    }
}
