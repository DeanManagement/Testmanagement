package com.deanmanagement.testmanagement.user.internal.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Tells a caller who presented an API key on the wrong path where that key actually works.
 *
 * <p>Written after watching an agent spend an afternoon on this instance. It had a valid key, hit
 * {@code /api/projects}, got Spring's bare 403, and reasonably concluded the key was the wrong kind
 * of credential — so it went looking for a login endpoint, guessed the product was TM4J, and
 * eventually asked its user for a password. It never tried {@code /api/mcp}, because nothing it
 * could see suggested that path existed.
 *
 * <p>The response says only where keys are accepted, which is public information — it is in the
 * setup documentation and on the API-key screen. It deliberately does not say whether the presented
 * key is valid, so this cannot be used to test keys.
 */
class ApiKeySignpostEntryPoint implements AuthenticationEntryPoint {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String KEY_PREFIX = "tm_";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        if (!carriesApiKey(request)) {
            // No key in play: leave the ordinary 403 alone rather than advertising the API-key
            // surface to every anonymous request.
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"error":"An API key is not accepted on this path.",\
                "hint":"API keys work on /api/mcp (MCP tools for agents) and /api/external/** \
                (CI result ingestion). Everything else needs a signed-in user's token.",\
                "mcpEndpoint":"/api/mcp"}""");
    }

    /** Both header forms the API-key filter accepts, so the hint reaches either style of caller. */
    private static boolean carriesApiKey(HttpServletRequest request) {
        String header = request.getHeader(API_KEY_HEADER);
        if (header != null && !header.isBlank()) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        return authorization != null && authorization.startsWith("Bearer " + KEY_PREFIX);
    }
}
