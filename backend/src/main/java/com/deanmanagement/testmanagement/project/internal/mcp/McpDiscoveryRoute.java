package com.deanmanagement.testmanagement.project.internal.mcp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;

/**
 * Answers a plain {@code GET /api/mcp} with what a client needs in order to connect (PRD-025).
 *
 * <p>MCP cannot be discovered by probing: a client must be configured with the endpoint, transport
 * and auth scheme before it can say anything at all. Someone told only "there is an MCP server at
 * this host" has no way to find the rest — and what they got here was a bare 405, because the
 * transport registers POST only. This turns that dead end into an answer.
 *
 * <p>A {@link RouterFunction} rather than a {@code @RestController}, which was the first attempt
 * and did not work: {@code RouterFunctionMapping} sits at order -1, ahead of
 * {@code RequestMappingHandlerMapping} at 0, so Spring AI's route matched {@code /api/mcp} first,
 * found the method was not POST and returned 405 without ever consulting the controller. Being in
 * the same mapping, ordered ahead of it, is the only way to be asked at all.
 *
 * <p>Unauthenticated on purpose — the reader is someone who has not managed to authenticate yet —
 * and registered only when MCP is switched on, so a disabled feature still reports as absent
 * rather than describing itself.
 */
@Configuration
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class McpDiscoveryRoute {

    public record McpDescriptor(String server, String version, String protocol, String transport,
                                String endpoint, List<String> authentication, String documentation,
                                String note) {}

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public RouterFunction<ServerResponse> mcpDiscoveryDescriptorRoute(
            @Value("${spring.ai.mcp.server.version:1.0.0}") String version) {

        McpDescriptor descriptor = new McpDescriptor(
                "testmanagement",
                version,
                "Model Context Protocol",
                "streamable-http (stateless)",
                "/api/mcp",
                List.of("Authorization: Bearer tm_…", "X-API-Key: tm_…"),
                "https://github.com/DeanManagement/Testmanagement/blob/main/docs/MCP_SETUP.md",
                "POST JSON-RPC here with an MCP client. Keys are created per project under "
                        + "Settings → API Keys and are scoped to that one project.");

        return RouterFunctions.route()
                .GET("/api/mcp", request -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(descriptor))
                .build();
    }
}
