package com.deanmanagement.testmanagement.user.internal.sso;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * SSO administration (PRD-012). System admins only — these endpoints control who can get into the
 * instance at all, so they sit behind the same guard as user management.
 */
@RestController
@RequestMapping("/api/admin/sso")
@Tag(name = "SSO Administration", description = "OpenID Connect providers and authentication settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SsoAdminController {

    private final SsoProviderService providerService;

    @GetMapping("/providers")
    public List<SsoProviderResponse> list() {
        return providerService.findAll();
    }

    @GetMapping("/providers/{id}")
    public SsoProviderResponse get(@PathVariable UUID id) {
        return providerService.find(id);
    }

    @PostMapping("/providers")
    @ResponseStatus(HttpStatus.CREATED)
    public SsoProviderResponse create(@Valid @RequestBody SaveSsoProviderRequest request) {
        return providerService.create(request);
    }

    @PutMapping("/providers/{id}")
    public SsoProviderResponse update(@PathVariable UUID id,
                                      @Valid @RequestBody SaveSsoProviderRequest request) {
        return providerService.update(id, request);
    }

    @DeleteMapping("/providers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        providerService.delete(id);
    }

    /** Reads the issuer's discovery document. 502 if it is unreachable or not an OIDC issuer. */
    @PostMapping("/providers/{id}/test")
    public ResponseEntity<Void> test(@PathVariable UUID id) {
        providerService.testConnection(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/settings")
    public AuthSettingsResponse settings() {
        return providerService.authSettings();
    }

    @PutMapping("/settings")
    public AuthSettingsResponse updateSettings(@Valid @RequestBody UpdateAuthSettingsRequest request) {
        return providerService.updateAuthSettings(request);
    }
}
