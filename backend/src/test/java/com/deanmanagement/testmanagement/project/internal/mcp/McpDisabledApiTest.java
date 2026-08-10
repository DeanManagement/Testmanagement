package com.deanmanagement.testmanagement.project.internal.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD-025 §3.3. With {@code app.mcp.enabled=false} — the default — the endpoint must be absent,
 * not merely unauthorized.
 *
 * <p>The distinction is the whole test. An {@code @Order(1)} security chain claims a request before
 * the dispatcher ever runs, so a {@code securityMatcher} that listed {@code /api/mcp/**}
 * unconditionally would answer 401 for a feature that is switched off — advertising it, and
 * inviting someone to go looking for a key that would not help.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class McpDisabledApiTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @Test
    void theEndpointDoesNotExist() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/mcp"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"))
                .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode())
                    .as("a disabled feature should be absent, not unauthorized")
                    .isNotEqualTo(401)
                    .isNotEqualTo(200);
        }
    }

    @Test
    void noMcpServerBeansAreRegistered() {
        assertThat(context.getBeanNamesForType(
                io.modelcontextprotocol.server.McpStatelessSyncServer.class))
                .as("Spring AI should not have wired a server at all")
                .isEmpty();
    }
}
