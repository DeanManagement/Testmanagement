package com.deanmanagement.testmanagement.user.internal.sso;

import com.deanmanagement.testmanagement.shared.crypto.AesGcmCipher;
import com.deanmanagement.testmanagement.shared.exception.DuplicateKeyException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException;
import com.deanmanagement.testmanagement.shared.net.OutboundUrlValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CRUD for OIDC provider registrations (PRD-012), plus the instance-wide auth settings.
 *
 * <p>Every mutation invalidates the {@link DynamicClientRegistrationRepository} cache, because a
 * changed issuer or secret must take effect on the next login attempt rather than after a restart.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SsoProviderService {

    private static final String ISSUER_LABEL = "Issuer URL";
    private static final int MAX_ERROR_LENGTH = 500;

    private final SsoProviderRepository providerRepository;
    private final AuthSettingsRepository authSettingsRepository;
    private final AesGcmCipher secretCipher;
    private final SsoProperties properties;
    private final DynamicClientRegistrationRepository registrationRepository;

    public List<SsoProviderResponse> findAll() {
        return providerRepository.findAllByOrderByDisplayNameAsc().stream()
                .map(SsoProviderService::toResponse)
                .toList();
    }

    public SsoProviderResponse find(UUID id) {
        return toResponse(require(id));
    }

    /** What the login screen renders. Only active providers, and only their slug and label. */
    public AuthConfigResponse authConfig() {
        List<AuthConfigResponse.AuthProviderSummary> providers =
                providerRepository.findByActiveTrueOrderByDisplayNameAsc().stream()
                        .map(p -> new AuthConfigResponse.AuthProviderSummary(p.getSlug(), p.getDisplayName()))
                        .toList();
        return new AuthConfigResponse(localLoginEnabled(), providers);
    }

    @Transactional
    public SsoProviderResponse create(SaveSsoProviderRequest request) {
        if (providerRepository.existsBySlug(request.slug())) {
            throw new DuplicateKeyException("slug", request.slug());
        }
        if (isBlank(request.clientSecret())) {
            throw new IllegalArgumentException("A client secret is required when adding a provider");
        }
        SsoProvider provider = new SsoProvider();
        provider.setSlug(request.slug());
        apply(provider, request);
        provider = providerRepository.save(provider);
        registrationRepository.invalidate();
        return toResponse(provider);
    }

    @Transactional
    public SsoProviderResponse update(UUID id, SaveSsoProviderRequest request) {
        SsoProvider provider = require(id);
        if (!provider.getSlug().equals(request.slug())) {
            // The slug is baked into the callback URL registered at the IdP, so changing it would
            // silently break logins until someone updates the IdP too.
            throw new IllegalArgumentException("The slug of an existing provider cannot be changed");
        }
        apply(provider, request);
        provider = providerRepository.save(provider);
        registrationRepository.invalidate();
        return toResponse(provider);
    }

    @Transactional
    public void delete(UUID id) {
        SsoProvider provider = require(id);
        // sso_identities cascade with the provider row. Anyone who could only sign in this way
        // loses access, which is the point of deleting a provider.
        providerRepository.delete(provider);
        registrationRepository.invalidate();
    }

    /**
     * Fetches the issuer's discovery document, proving the URL is reachable and really is an OIDC
     * issuer before anyone tries to log in through it.
     */
    @Transactional
    public void testConnection(UUID id) {
        SsoProvider provider = require(id);
        try {
            registrationRepository.buildRegistration(provider);
            clearError(provider);
        } catch (RuntimeException e) {
            recordError(provider, e.getMessage());
            throw new UpstreamServiceException(
                    "Could not read the OpenID configuration from " + provider.getIssuerUri());
        }
    }

    public boolean localLoginEnabled() {
        return settings().isLocalLoginEnabled();
    }

    public AuthSettingsResponse authSettings() {
        return new AuthSettingsResponse(localLoginEnabled());
    }

    @Transactional
    public AuthSettingsResponse updateAuthSettings(UpdateAuthSettingsRequest request) {
        if (Boolean.FALSE.equals(request.localLoginEnabled())
                && providerRepository.findByActiveTrueOrderByDisplayNameAsc().isEmpty()) {
            // Without this, an admin could disable password login on an instance with no working
            // SSO and lock out every non-admin immediately.
            throw new IllegalArgumentException(
                    "Configure and activate at least one SSO provider before disabling local login");
        }
        AuthSettings settings = settings();
        settings.setLocalLoginEnabled(request.localLoginEnabled());
        authSettingsRepository.save(settings);
        return new AuthSettingsResponse(settings.isLocalLoginEnabled());
    }

    @Transactional
    public void recordError(SsoProvider provider, String message) {
        String trimmed = message == null ? "Unknown error"
                : message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH));
        provider.setLastError(trimmed);
        provider.setLastErrorAt(Instant.now());
        providerRepository.save(provider);
    }

    public String decryptSecret(SsoProvider provider) {
        return secretCipher.decrypt(provider.getClientSecretEncrypted());
    }

    // ---- internals --------------------------------------------------------

    private void apply(SsoProvider provider, SaveSsoProviderRequest request) {
        OutboundUrlValidator.validate(request.issuerUri(), ISSUER_LABEL,
                properties.requireHttps(), properties.allowPrivateIssuers());

        provider.setDisplayName(request.displayName().trim());
        provider.setIssuerUri(trimTrailingSlash(request.issuerUri().trim()));
        provider.setClientId(request.clientId().trim());
        provider.setScopes(normaliseScopes(request.scopes()));
        provider.setEmailClaim(defaulted(request.emailClaim(), "email"));
        provider.setNameClaim(defaulted(request.nameClaim(), "name"));
        provider.setAdminClaim(emptyToNull(request.adminClaim()));
        provider.setAdminClaimValue(emptyToNull(request.adminClaimValue()));
        provider.setTrustEmailForLinking(Boolean.TRUE.equals(request.trustEmailForLinking()));
        provider.setAutoProvision(request.autoProvision() == null || request.autoProvision());
        provider.setActive(request.active() == null || request.active());

        if (!isBlank(request.clientSecret())) {
            provider.setClientSecretEncrypted(secretCipher.encrypt(request.clientSecret().trim()));
            provider.setLastError(null);
            provider.setLastErrorAt(null);
        }

        if (provider.getAdminClaim() != null && provider.getAdminClaimValue() == null) {
            throw new IllegalArgumentException("An admin claim needs the value that grants admin");
        }
    }

    /** {@code openid} is mandatory for OIDC, so it is added rather than left to the admin. */
    private static String normaliseScopes(String scopes) {
        Set<String> values = new LinkedHashSet<>();
        values.add("openid");
        if (scopes != null && !scopes.isBlank()) {
            values.addAll(Arrays.stream(scopes.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        } else {
            values.add("profile");
            values.add("email");
        }
        return String.join(",", values);
    }

    private AuthSettings settings() {
        return authSettingsRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    AuthSettings created = new AuthSettings();
                    created.setLocalLoginEnabled(true);
                    return authSettingsRepository.save(created);
                });
    }

    private SsoProvider require(UUID id) {
        return providerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SsoProvider", id));
    }

    private void clearError(SsoProvider provider) {
        if (provider.getLastError() != null) {
            provider.setLastError(null);
            provider.setLastErrorAt(null);
            providerRepository.save(provider);
        }
    }

    static SsoProviderResponse toResponse(SsoProvider provider) {
        return new SsoProviderResponse(
                provider.getId(),
                provider.getSlug(),
                provider.getDisplayName(),
                provider.getIssuerUri(),
                provider.getClientId(),
                provider.getClientSecretEncrypted() != null && !provider.getClientSecretEncrypted().isBlank(),
                provider.getScopes(),
                provider.getEmailClaim(),
                provider.getNameClaim(),
                provider.getAdminClaim(),
                provider.getAdminClaimValue(),
                provider.isTrustEmailForLinking(),
                provider.isAutoProvision(),
                provider.isActive(),
                provider.getLastError(),
                provider.getLastErrorAt(),
                provider.getUpdatedAt());
    }

    private static Optional<String> trimmed(String value) {
        return Optional.ofNullable(value).map(String::trim).filter(s -> !s.isEmpty());
    }

    private static String defaulted(String value, String fallback) {
        return trimmed(value).orElse(fallback);
    }

    private static String emptyToNull(String value) {
        return trimmed(value).orElse(null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
