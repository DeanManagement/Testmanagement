package com.deanmanagement.testmanagement.project.internal.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A switched-off feature must not describe itself. The descriptor is helpful precisely because it
 * says "there is an MCP server here"; when there is not, saying so would be a lie that sends
 * someone hunting for an endpoint that will never answer.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class McpDiscoveryDisabledApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theDescriptorIsAbsentWhenMcpIsDisabled() throws Exception {
        mockMvc.perform(get("/api/mcp"))
                .andExpect(status().isNotFound());
    }
}
