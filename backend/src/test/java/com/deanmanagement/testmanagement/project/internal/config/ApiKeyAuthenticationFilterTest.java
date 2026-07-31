package com.deanmanagement.testmanagement.project.internal.config;

import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService;
import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService.ValidatedKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private FilterChain filterChain;

    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthenticationFilter(apiKeyService);
        SecurityContextHolder.clearContext();
    }

    @Test
    void validKey_setsAuthenticationAndContinues() throws ServletException, IOException {
        UUID keyId = UUID.randomUUID();
        ValidatedKey apiKey = new ValidatedKey(keyId, "CI Key", null); // legacy/global key

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/external/projects/123/test-runs");
        request.addHeader("X-API-Key", "tm_validkey123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(apiKeyService.validateKey("tm_validkey123")).thenReturn(Optional.of(apiKey));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(apiKeyService).updateLastUsed(keyId);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("api-key:CI Key");
    }

    @Test
    void missingHeader_returns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/external/projects/123/test-runs");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Missing X-API-Key header");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void blankHeader_returns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/external/projects/123/test-runs");
        request.addHeader("X-API-Key", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void invalidKey_returns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/external/projects/123/test-runs");
        request.addHeader("X-API-Key", "tm_invalidkey");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(apiKeyService.validateKey("tm_invalidkey")).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid or revoked API key");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void nonExternalPath_isSkipped() throws ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");

        boolean shouldNotFilter = filter.shouldNotFilter(request);

        assertThat(shouldNotFilter).isTrue();
    }

    @Test
    void externalPath_isNotSkipped() throws ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/external/projects/123/test-runs");

        boolean shouldNotFilter = filter.shouldNotFilter(request);

        assertThat(shouldNotFilter).isFalse();
    }

    @Test
    void scopedKey_matchingProject_passes() throws ServletException, IOException {
        UUID keyId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/external/projects/DEMO/test-runs");
        request.addHeader("X-API-Key", "tm_scoped");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(apiKeyService.validateKey("tm_scoped"))
                .thenReturn(Optional.of(new ValidatedKey(keyId, "Scoped", "DEMO")));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void scopedKey_otherProject_returns403() throws ServletException, IOException {
        // PRD-021 §4.2: a project-scoped key must not touch another project.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/external/projects/OTHER/test-runs");
        request.addHeader("X-API-Key", "tm_scoped");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(apiKeyService.validateKey("tm_scoped"))
                .thenReturn(Optional.of(new ValidatedKey(UUID.randomUUID(), "Scoped", "DEMO")));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("not authorized for project OTHER");
        verify(filterChain, never()).doFilter(request, response);
        verify(apiKeyService, never()).updateLastUsed(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void extractProjectKey_parsesSegment() {
        assertThat(ApiKeyAuthenticationFilter.extractProjectKey("/api/external/projects/DEMO/test-runs")).isEqualTo("DEMO");
        assertThat(ApiKeyAuthenticationFilter.extractProjectKey("/api/external/projects/DEMO")).isEqualTo("DEMO");
        assertThat(ApiKeyAuthenticationFilter.extractProjectKey("/api/external/other")).isNull();
    }
}
