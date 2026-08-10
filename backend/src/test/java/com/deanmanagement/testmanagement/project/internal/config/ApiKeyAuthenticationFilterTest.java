package com.deanmanagement.testmanagement.project.internal.config;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
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

    private static final UUID DEMO_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID SERVICE_USER_ID = UUID.fromString("99999999-8888-7777-6666-555555555555");

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthenticationFilter(apiKeyService, false);
        SecurityContextHolder.clearContext();
    }

    /**
     * PRD-025 §3.2: the principal must be the service user's UUID. As the literal string
     * {@code "api-key:<name>"} it did not parse as a UUID, which made
     * {@code ProjectAccessService.currentUserId()} return null and {@code ProjectRoleAspect} skip
     * the role check entirely.
     */
    @Test
    void validKey_authenticatesAsItsServiceUser() throws ServletException, IOException {
        UUID keyId = UUID.randomUUID();
        ValidatedKey apiKey = new ValidatedKey(keyId, "CI Key", DEMO_ID, "DEMO", SERVICE_USER_ID, ProjectRole.TESTER);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/external/projects/DEMO/test-runs");
        request.addHeader("X-API-Key", "tm_validkey123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(apiKeyService.validateKey("tm_validkey123")).thenReturn(Optional.of(apiKey));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(apiKeyService).updateLastUsed(keyId);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(SERVICE_USER_ID.toString());
        assertThat(UUID.fromString(authentication.getName())).isEqualTo(SERVICE_USER_ID);
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_API_KEY", "ROLE_USER");
    }

    @Test
    void legacyGlobalKey_isRejectedByDefault() throws ServletException, IOException {
        // PRD-025 §3.2: no project means no membership, so nothing can authorize it.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/external/projects/123/test-runs");
        request.addHeader("X-API-Key", "tm_legacy");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(apiKeyService.validateKey("tm_legacy"))
                .thenReturn(Optional.of(new ValidatedKey(UUID.randomUUID(), "Legacy", null, null, null, ProjectRole.TESTER)));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("not scoped to a project");
        verify(filterChain, never()).doFilter(request, response);
    }

    /**
     * A key whose backfill has not run yet (or failed) must fail closed. Without this it would fall
     * through to the legacy non-UUID principal and silently skip every role check — the exact bug
     * PRD-025 §3.2 exists to remove.
     */
    @Test
    void projectScopedKeyWithoutAServiceUser_isRejected() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/external/projects/DEMO/test-runs");
        request.addHeader("X-API-Key", "tm_notready");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(apiKeyService.validateKey("tm_notready"))
                .thenReturn(Optional.of(new ValidatedKey(UUID.randomUUID(), "Not ready", DEMO_ID, "DEMO", null, ProjectRole.TESTER)));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("not ready yet");
        verify(filterChain, never()).doFilter(request, response);
        verify(apiKeyService, never()).updateLastUsed(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void legacyGlobalKey_stillWorksWhenExplicitlyAllowed() throws ServletException, IOException {
        ApiKeyAuthenticationFilter permissive = new ApiKeyAuthenticationFilter(apiKeyService, true);
        UUID keyId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/external/projects/123/test-runs");
        request.addHeader("X-API-Key", "tm_legacy");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(apiKeyService.validateKey("tm_legacy"))
                .thenReturn(Optional.of(new ValidatedKey(keyId, "Legacy", null, null, null, ProjectRole.TESTER)));

        permissive.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("api-key:Legacy");
    }

    @Test
    void missingHeader_returns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/external/projects/123/test-runs");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        // Names both accepted forms since PRD-025 widened this to Authorization: Bearer.
        assertThat(response.getContentAsString()).contains("Missing API key");
        assertThat(response.getHeader("WWW-Authenticate")).contains("Bearer");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void bearerToken_isAcceptedForMcpClients() throws ServletException, IOException {
        UUID keyId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mcp");
        request.addHeader("Authorization", "Bearer tm_validkey123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(apiKeyService.validateKey("tm_validkey123")).thenReturn(Optional.of(
                new ValidatedKey(keyId, "Agent", DEMO_ID, "DEMO", SERVICE_USER_ID, ProjectRole.TESTER)));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(SERVICE_USER_ID.toString());
    }

    @Test
    void bearerToken_thatIsNotAnApiKey_isIgnored() throws ServletException, IOException {
        // A JWT presented as a bearer token must not be accepted by the API-key chain. The tm_
        // prefix is the whole discriminator, so this is the test that keeps the two token types
        // from bleeding into each other.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mcp");
        request.addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.sig");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void mcpPath_isNotSkipped() throws ServletException {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/mcp"))).isFalse();
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
                .thenReturn(Optional.of(new ValidatedKey(keyId, "Scoped", DEMO_ID, "DEMO", SERVICE_USER_ID, ProjectRole.TESTER)));

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
                .thenReturn(Optional.of(new ValidatedKey(UUID.randomUUID(), "Scoped", DEMO_ID, "DEMO", SERVICE_USER_ID, ProjectRole.TESTER)));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("not authorized for project OTHER");
        verify(filterChain, never()).doFilter(request, response);
        verify(apiKeyService, never()).updateLastUsed(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void scopedKey_matchingProjectUuid_passes() throws ServletException, IOException {
        // The URL may name the project by UUID instead of key; the scope check must accept both.
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/external/projects/" + DEMO_ID + "/test-runs");
        request.addHeader("X-API-Key", "tm_scoped");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(apiKeyService.validateKey("tm_scoped"))
                .thenReturn(Optional.of(new ValidatedKey(UUID.randomUUID(), "Scoped", DEMO_ID, "DEMO", SERVICE_USER_ID, ProjectRole.TESTER)));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void scopedKey_otherProjectUuid_returns403() throws ServletException, IOException {
        UUID otherId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/external/projects/" + otherId + "/test-runs");
        request.addHeader("X-API-Key", "tm_scoped");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(apiKeyService.validateKey("tm_scoped"))
                .thenReturn(Optional.of(new ValidatedKey(UUID.randomUUID(), "Scoped", DEMO_ID, "DEMO", SERVICE_USER_ID, ProjectRole.TESTER)));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void extractProjectRef_parsesSegment() {
        assertThat(ApiKeyAuthenticationFilter.extractProjectRef("/api/external/projects/DEMO/test-runs")).isEqualTo("DEMO");
        assertThat(ApiKeyAuthenticationFilter.extractProjectRef("/api/external/projects/DEMO")).isEqualTo("DEMO");
        assertThat(ApiKeyAuthenticationFilter.extractProjectRef("/api/external/other")).isNull();
    }
}
