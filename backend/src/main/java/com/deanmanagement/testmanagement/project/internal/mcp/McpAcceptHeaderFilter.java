package com.deanmanagement.testmanagement.project.internal.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Explains the one rejection a hand-written MCP client is most likely to hit.
 *
 * <p>Streamable-HTTP requires the caller to accept <em>both</em> {@code application/json} and
 * {@code text/event-stream}, because the server chooses per request which one to answer with.
 * Spring AI enforces that with a bare 400 and a zero-byte body — no message, no header name,
 * nothing. An agent hand-rolling the protocol sends {@code Accept: application/json}, which is the
 * obvious guess for a JSON-RPC call, or leaves curl's default {@code *&#47;*} in place, and gets an
 * empty 400 it cannot learn anything from. One did exactly that, tried several body shapes looking
 * for the fault, and concluded the transport was mismatched.
 *
 * <p>The status stays 400 — the request genuinely is malformed — but the body now says which header
 * is wrong and what it has to contain, shaped as a JSON-RPC error so a client that parses responses
 * can surface it.
 */
class McpAcceptHeaderFilter extends OncePerRequestFilter {

    private final String mcpEndpoint;

    McpAcceptHeaderFilter(String mcpEndpoint) {
        this.mcpEndpoint = mcpEndpoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !mcpEndpoint.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (acceptsBoth(request.getHeader("Accept"))) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"jsonrpc":"2.0","id":null,"error":{"code":-32600,\
                "message":"The Accept header must list both application/json and text/event-stream.",\
                "data":{"required":"Accept: application/json, text/event-stream",\
                "received":"%s",\
                "why":"Streamable-HTTP lets the server reply with either a JSON body or an SSE \
                stream, so a client has to accept both.",\
                "documentation":"%s"}}}"""
                .formatted(escape(request.getHeader("Accept")), McpDiscoveryRoute.DOCUMENTATION_URL));
    }

    /**
     * Both media types must be present. A wildcard is deliberately not accepted: Spring AI rejects
     * it too, so treating it as sufficient here would move the same silent 400 one step later.
     */
    private static boolean acceptsBoth(String accept) {
        if (accept == null) {
            return false;
        }
        String normalised = accept.toLowerCase();
        return normalised.contains(MediaType.APPLICATION_JSON_VALUE)
                && normalised.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    private static String escape(String value) {
        return value == null ? "(none)" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
