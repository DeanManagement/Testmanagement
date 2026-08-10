package com.deanmanagement.testmanagement.project.internal.config;

import com.deanmanagement.testmanagement.project.internal.mcp.McpFilters;
import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class ApiKeySecurityConfig {

    private static final String[] EXTERNAL_ONLY = {"/api/external/**"};
    private static final String[] EXTERNAL_AND_MCP = {"/api/external/**", "/api/mcp/**"};

    private final ApiKeyService apiKeyService;
    private final boolean allowLegacyGlobalKeys;
    private final boolean mcpEnabled;

    public ApiKeySecurityConfig(
            ApiKeyService apiKeyService,
            @Value("${app.api-keys.allow-legacy-global:false}") boolean allowLegacyGlobalKeys,
            @Value("${app.mcp.enabled:false}") boolean mcpEnabled) {
        this.apiKeyService = apiKeyService;
        this.allowLegacyGlobalKeys = allowLegacyGlobalKeys;
        this.mcpEnabled = mcpEnabled;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiKeyFilterChain(HttpSecurity http) throws Exception {
        http
                // PRD-025 §3.3: /api/mcp is claimed only when the feature is on. An @Order(1) chain
                // answers before the dispatcher runs, so matching unconditionally would return 401
                // for a switched-off feature instead of letting it 404 like anything else absent.
                .securityMatcher(mcpEnabled ? EXTERNAL_AND_MCP : EXTERNAL_ONLY)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new ApiKeyAuthenticationFilter(apiKeyService, allowLegacyGlobalKeys),
                        UsernamePasswordAuthenticationFilter.class)
                // After authentication, so an anonymous prober still just gets 401 — and before
                // the transport, whose own answer to a bad Accept header is an empty 400.
                .addFilterAfter(McpFilters.acceptHeader(), ApiKeyAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // The discovery descriptor, for a client that has not been configured yet
                        // and therefore cannot authenticate. GET is not part of the stateless
                        // streamable-HTTP transport, so this takes nothing away from it.
                        .requestMatchers(HttpMethod.GET, "/api/mcp").permitAll()
                        .anyRequest().hasRole("API_KEY"));
        return http.build();
    }
}
