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
    private static final String EXTERNAL_PATH_PREFIX = "/api/external/";
    private static final String EXTERNAL_PROJECT_PREFIX = "/api/external/projects/";

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(EXTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKeyHeader = request.getHeader(API_KEY_HEADER);

        if (apiKeyHeader == null || apiKeyHeader.isBlank()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing X-API-Key header");
            return;
        }

        Optional<ValidatedKey> apiKey = apiKeyService.validateKey(apiKeyHeader);

        if (apiKey.isEmpty()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or revoked API key");
            return;
        }

        // PRD-021 §4.2: a project-scoped key may only touch its own project. Enforced here,
        // centrally, so every current and future /api/external/projects/{key}/** endpoint is
        // covered. Legacy keys (no scope) pass with a deprecation warning.
        String keyScope = apiKey.get().projectKey();
        String pathProject = extractProjectKey(request.getRequestURI());
        if (keyScope != null && pathProject != null && !keyScope.equals(pathProject)) {
            reject(response, HttpServletResponse.SC_FORBIDDEN,
                    "API key is not authorized for project " + pathProject);
            return;
        }
        if (keyScope == null) {
            log.warn("Legacy global API key '{}' used for {} — scope it to a project; "
                    + "global keys will be rejected in a future release.",
                    apiKey.get().name(), request.getRequestURI());
        }

        apiKeyService.updateLastUsed(apiKey.get().id());

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "api-key:" + apiKey.get().name(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_API_KEY"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    /** @return the {projectKey} segment of /api/external/projects/{projectKey}/…, or null. */
    static String extractProjectKey(String uri) {
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
