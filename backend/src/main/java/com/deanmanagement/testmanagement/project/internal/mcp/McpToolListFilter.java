package com.deanmanagement.testmanagement.project.internal.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.Iterator;

/**
 * Drops the tools a restricted API key may not use from its {@code tools/list} response
 * (PRD-027 §9), so their descriptions stop riding along on every turn of an agent that will never
 * call them. That saving is the whole reason the allow-list exists.
 *
 * <p><b>Why an HTTP filter.</b> The list is built by
 * {@code McpStatelessAsyncServer.toolsListRequestHandler}, which is private and returns every
 * registered tool, and {@code WebMvcStatelessServerTransport} is final, so there is no seam inside
 * the SDK to vary it per caller. The JSON-RPC envelope, on the other hand, is fixed by the MCP
 * specification rather than by a library version.
 *
 * <p><b>Not the security boundary.</b> {@link McpCallerContext#requireAllowed} refuses the call
 * itself. This filter therefore fails open: anything it does not recognise — a batch, an SSE reply,
 * a body that is not JSON — is passed through untouched, and the worst outcome is a longer list.
 *
 * <p>The list still does not vary call to call, only key to key, so a client caching it per
 * connection sees a stable answer.
 */
class McpToolListFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(McpToolListFilter.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String TOOLS_LIST = "tools/list";
    /** A JSON-RPC request is a few hundred bytes; this only bounds what is kept for inspection. */
    private static final int MAX_INSPECTED_REQUEST_BYTES = 64 * 1024;

    private final String mcpEndpoint;
    private final McpCallerContext callerContext;
    private final McpToolGroups toolGroups;

    McpToolListFilter(String mcpEndpoint, McpCallerContext callerContext, McpToolGroups toolGroups) {
        this.mcpEndpoint = mcpEndpoint;
        this.callerContext = callerContext;
        this.toolGroups = toolGroups;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !mcpEndpoint.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        McpCallerContext.Caller caller = restrictedCallerOrNull();
        if (caller == null) {
            filterChain.doFilter(request, response);
            return;
        }

        var cachedRequest = new ContentCachingRequestWrapper(request, MAX_INSPECTED_REQUEST_BYTES);
        var cachedResponse = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(cachedRequest, cachedResponse);

        // The request body is only available now: the wrapper records it as the transport reads.
        if (isToolsList(cachedRequest.getContentAsByteArray())) {
            byte[] trimmed = trim(cachedResponse.getContentAsByteArray(), caller);
            if (trimmed != null) {
                cachedResponse.resetBuffer();
                cachedResponse.getOutputStream().write(trimmed);
            }
        }
        cachedResponse.copyBodyToResponse();
    }

    /** Null for an unrestricted key, and for a caller the tools will refuse anyway. */
    private McpCallerContext.Caller restrictedCallerOrNull() {
        try {
            McpCallerContext.Caller caller = callerContext.require();
            return caller.isRestricted() ? caller : null;
        } catch (McpToolException notAProjectKey) {
            return null;
        } finally {
            // require() publishes the caller for the auditor; this is not a tool call.
            McpCallerHolder.clear();
        }
    }

    private static boolean isToolsList(byte[] requestBody) {
        try {
            JsonNode request = JSON.readTree(requestBody);
            return request != null && TOOLS_LIST.equals(request.path("method").asString(""));
        } catch (JacksonException notJson) {
            return false;
        }
    }

    /** @return the rewritten body, or null to leave the response exactly as it was */
    private byte[] trim(byte[] responseBody, McpCallerContext.Caller caller) {
        try {
            JsonNode response = JSON.readTree(responseBody);
            if (response == null || !(response.path("result").path("tools") instanceof ArrayNode tools)) {
                return null;
            }
            for (Iterator<JsonNode> each = tools.iterator(); each.hasNext(); ) {
                String name = each.next().path("name").asString("");
                if (toolGroups.groupOf(name).filter(caller::allows).isEmpty()) {
                    each.remove();
                }
            }
            return JSON.writeValueAsBytes((ObjectNode) response);
        } catch (JacksonException notJson) {
            log.warn("Could not trim tools/list for API key {}; returning the full list. "
                    + "Calls outside its tool groups are still refused.", caller.apiKeyId(), notJson);
            return null;
        }
    }
}
