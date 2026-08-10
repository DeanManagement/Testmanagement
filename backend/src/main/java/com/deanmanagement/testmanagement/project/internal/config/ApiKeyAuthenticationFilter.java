package com.deanmanagement.testmanagement.project.internal.config;

import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService;
import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService.ValidatedKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    /** Raw keys are minted with this prefix, which is what tells a key from a JWT. */
    private static final String KEY_PREFIX = "tm_";
    private static final String EXTERNAL_PATH_PREFIX = "/api/external/";
    private static final String EXTERNAL_PROJECT_PREFIX = "/api/external/projects/";
    private static final String MCP_PATH_PREFIX = "/api/mcp";

    private final ApiKeyService apiKeyService;

    /**
     * PRD-025 §3.2: legacy keys have no project and therefore no service user, so they cannot be
     * authorized like every other caller. They are rejected unless an operator explicitly opts back
     * in while re-issuing them.
     */
    private final boolean allowLegacyGlobalKeys;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService, boolean allowLegacyGlobalKeys) {
        this.apiKeyService = apiKeyService;
        this.allowLegacyGlobalKeys = allowLegacyGlobalKeys;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // GET /api/mcp is the unauthenticated discovery descriptor — the one thing here a caller
        // without a working key is meant to be able to read.
        if ("GET".equals(request.getMethod()) && MCP_PATH_PREFIX.equals(uri)) {
            return true;
        }
        return !uri.startsWith(EXTERNAL_PATH_PREFIX) && !isMcpPath(uri);
    }

    /**
     * Exact match or a path segment below it — deliberately not {@code startsWith}, which would
     * also swallow {@code /api/mcp-activity}, the admin audit endpoint that must stay on the JWT
     * chain where {@code hasRole('ADMIN')} applies.
     */
    private static boolean isMcpPath(String uri) {
        return uri.equals(MCP_PATH_PREFIX) || uri.startsWith(MCP_PATH_PREFIX + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String presentedKey = extractKey(request);

        if (presentedKey == null) {
            // MCP clients speak the OAuth bearer convention and expect this challenge; without it
            // they surface a bare protocol error rather than "you need to configure a token".
            response.setHeader("WWW-Authenticate", "Bearer realm=\"testmanagement\"");
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Missing API key. Send X-API-Key, or Authorization: Bearer tm_…");
            return;
        }

        Optional<ValidatedKey> apiKey = apiKeyService.validateKey(presentedKey);

        if (apiKey.isEmpty()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or revoked API key");
            return;
        }

        // PRD-021 §4.2: a project-scoped key may only touch its own project. Enforced here,
        // centrally, so every current and future /api/external/projects/{ref}/** endpoint is
        // covered. Legacy keys (no scope) pass with a deprecation warning.
        String keyScope = apiKey.get().projectKey();
        String pathProject = extractProjectRef(request.getRequestURI());
        if (keyScope != null && pathProject != null && !matchesScope(apiKey.get(), pathProject)) {
            reject(response, HttpServletResponse.SC_FORBIDDEN,
                    "API key is not authorized for project " + pathProject);
            return;
        }
        if (keyScope == null) {
            if (!allowLegacyGlobalKeys) {
                log.warn("Rejected legacy global API key '{}' for {} — re-create it scoped to a "
                        + "project, or set app.api-keys.allow-legacy-global=true to keep it working "
                        + "while you migrate.", apiKey.get().name(), request.getRequestURI());
                reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "API key is not scoped to a project. Re-create it from the admin settings.");
                return;
            }
            log.warn("Legacy global API key '{}' used for {} — it has no service user, so project "
                    + "role checks cannot apply to it. Re-create it scoped to a project.",
                    apiKey.get().name(), request.getRequestURI());
        }

        // A project-scoped key without a service user cannot be authorized — it would fall back to
        // the old non-UUID principal and silently skip every role check. That state is transient
        // (ApplicationRunners finish after the web server starts) or permanent (the backfill failed
        // for this key); either way, refusing is the only safe answer.
        if (apiKey.get().projectId() != null && apiKey.get().serviceUserId() == null) {
            log.warn("API key '{}' has no service account yet — rejecting {}",
                    apiKey.get().name(), request.getRequestURI());
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "API key is not ready yet. Retry shortly, or re-create it from the admin settings.");
            return;
        }

        apiKeyService.updateLastUsed(apiKey.get().id());

        SecurityContextHolder.getContext().setAuthentication(authenticationFor(apiKey.get()));

        filterChain.doFilter(request, response);
    }

    /**
     * PRD-025 §3.2: the principal is the service user's UUID, so {@code currentUserId()} resolves,
     * {@code @RequireProjectRole} enforces, and {@code created_by} is populated on anything the key
     * writes. {@code ROLE_USER} comes along because the key now genuinely acts as a user.
     *
     * <p>Only a legacy project-less key (opt-in, see {@link #allowLegacyGlobalKeys}) still falls
     * back to the old non-UUID principal — it has no service user to name.
     */
    private static UsernamePasswordAuthenticationToken authenticationFor(ValidatedKey key) {
        if (key.serviceUserId() != null) {
            return new UsernamePasswordAuthenticationToken(
                    key.serviceUserId().toString(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_API_KEY"),
                            new SimpleGrantedAuthority("ROLE_USER")));
        }
        return new UsernamePasswordAuthenticationToken(
                "api-key:" + key.name(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_API_KEY")));
    }

    /**
     * Accepts either header form. {@code X-API-Key} is what the CI docs have always said; MCP
     * clients only know how to send a bearer token, so {@code Authorization: Bearer tm_…} works
     * too. The {@code tm_} prefix disambiguates a key from a JWT, so widening this cannot let a
     * session token in through the wrong chain.
     */
    private static String extractKey(HttpServletRequest request) {
        String header = request.getHeader(API_KEY_HEADER);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            if (token.startsWith(KEY_PREFIX)) {
                return token;
            }
        }
        return null;
    }

    /**
     * The URL may name the project by key or by UUID (both are shown in the UI and in API
     * responses), so the scope check has to accept either form. The key comparison stays
     * case-sensitive; only the UUID form is compared loosely, since its canonical text is
     * lowercase but callers routinely paste it uppercased.
     */
    private static boolean matchesScope(ValidatedKey key, String ref) {
        return ref.equals(key.projectKey())
                || (key.projectId() != null && key.projectId().toString().equalsIgnoreCase(ref));
    }

    /** @return the {projectRef} segment of /api/external/projects/{projectRef}/…, or null. */
    static String extractProjectRef(String uri) {
        if (!uri.startsWith(EXTERNAL_PROJECT_PREFIX)) {
            return null;
        }
        String rest = uri.substring(EXTERNAL_PROJECT_PREFIX.length());
        int slash = rest.indexOf('/');
        String key = slash == -1 ? rest : rest.substring(0, slash);
        return key.isEmpty() ? null : key;
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
