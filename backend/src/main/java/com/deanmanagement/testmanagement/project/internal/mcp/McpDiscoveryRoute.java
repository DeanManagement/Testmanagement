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
import java.util.Map;

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

    static final String DOCUMENTATION_URL =
            "https://github.com/DeanManagement/Testmanagement/blob/main/docs/MCP_SETUP.md";

    /**
     * Field order is the reading order, and it is load-bearing.
     *
     * <p>This descriptor has two audiences and was written for only one of them. A human who cannot
     * connect needs the transport and headers. An <em>agent</em> that fetches this URL to orient
     * itself needs to be told the opposite of what it finds useful: that it should not be driving
     * this endpoint by hand at all. The first draft led with a ready-to-paste curl command, which
     * is a working recipe for the worst way to use the server — so a model that read it copied it,
     * and lost the tool schemas, the argument validation and its client's tool permissions in the
     * process.
     *
     * <p>{@code howToUse} and {@code clientConfiguration} therefore come before
     * {@code manualExample}, and the curl is labelled as the debugging aid it is.
     */
    public record McpDescriptor(String server, String version, String protocol, String transport,
                                String endpoint, List<String> authentication,
                                String howToUse, ClientConfiguration clientConfiguration,
                                RequestRequirements requestRequirements, String manualExample,
                                String documentation, String note) {}

    /** The shape a client's config file wants, so it can be copied rather than reconstructed. */
    public record ClientConfiguration(Map<String, ServerEntry> mcpServers) {}

    public record ServerEntry(String type, String url, Map<String, String> headers) {}

    /**
     * Stated explicitly because this is where a client that speaks HTTP but not MCP will look, and
     * getting {@code Accept} wrong is the failure that costs people the most time — it comes back
     * as an empty 400 from the transport itself.
     */
    public record RequestRequirements(String method, String contentType, String accept,
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
                "Register this endpoint in your MCP client using clientConfiguration below. Its "
                        + "tools then appear in your tool list and you call them directly, by name. "
                        + "If you are an agent and the tools are not in your list, the server is "
                        + "not registered with your client — that is a configuration problem to "
                        + "report, not something to work around by sending JSON-RPC by hand. "
                        + "Driving this endpoint manually works but costs you the tool schemas, "
                        + "argument validation and your client's tool permissions.",
                new ClientConfiguration(Map.of("testmanagement", new ServerEntry(
                        "http", "https://<host>/api/mcp",
                        Map.of("Authorization", "Bearer tm_…")))),
                new RequestRequirements("POST", "application/json",
                        "application/json, text/event-stream",
                        "Both Accept types are required. Sending only application/json, or leaving "
                                + "a client's default */*, is rejected by the transport."),
                """
                For a human debugging a connection, or a CI smoke test — not the way an agent \
                should call these tools:
                curl -X POST https://<host>/api/mcp \\
                  -H 'Authorization: Bearer tm_…' \\
                  -H 'Content-Type: application/json' \\
                  -H 'Accept: application/json, text/event-stream' \\
                  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'""",
                DOCUMENTATION_URL,
                "POST JSON-RPC here with an MCP client. Keys are created per project under "
                        + "Settings → API Keys and are scoped to that one project.");

        return RouterFunctions.route()
                .GET("/api/mcp", request -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(descriptor))
                .build();
    }
}
