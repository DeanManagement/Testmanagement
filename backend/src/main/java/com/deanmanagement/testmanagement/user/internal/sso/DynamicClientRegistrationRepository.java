package com.deanmanagement.testmanagement.user.internal.sso;

import com.deanmanagement.testmanagement.shared.crypto.AesGcmCipher;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Serves Spring Security's client registrations from the database instead of application
 * properties, so providers can be added and edited without a restart (PRD-012 §3.3).
 *
 * <p>Building a registration performs OIDC discovery — a network round trip to the issuer — so
 * results are cached per slug. {@link #invalidate()} is called on every provider mutation; the
 * alternative, a timed cache, would leave an admin staring at stale behaviour after fixing a typo.
 *
 * <p>Inactive providers resolve to {@code null}, which makes Spring reject the authorization
 * request. Deactivating a provider therefore blocks new logins immediately.
 */
@Component
public class DynamicClientRegistrationRepository implements ClientRegistrationRepository {

    private final SsoProviderRepository providerRepository;
    private final AesGcmCipher secretCipher;
    private final Map<String, ClientRegistration> cache = new ConcurrentHashMap<>();

    public DynamicClientRegistrationRepository(SsoProviderRepository providerRepository,
                                               AesGcmCipher secretCipher) {
        this.providerRepository = providerRepository;
        this.secretCipher = secretCipher;
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        ClientRegistration cached = cache.get(registrationId);
        if (cached != null) {
            return cached;
        }
        return providerRepository.findBySlug(registrationId)
                .filter(SsoProvider::isActive)
                .map(provider -> {
                    ClientRegistration registration = buildRegistration(provider);
                    cache.put(registrationId, registration);
                    return registration;
                })
                .orElse(null);
    }

    /** Drops every cached registration. Cheap: they are rebuilt lazily on the next login. */
    public void invalidate() {
        cache.clear();
    }

    /**
     * Performs discovery against the issuer and assembles the registration.
     *
     * @throws RuntimeException if the issuer is unreachable or serves no OpenID configuration —
     *         the caller turns this into a recorded error on the provider row.
     */
    public ClientRegistration buildRegistration(SsoProvider provider) {
        Set<String> scopes = Arrays.stream(provider.getScopes().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        return ClientRegistrations.fromIssuerLocation(provider.getIssuerUri())
                .registrationId(provider.getSlug())
                .clientId(provider.getClientId())
                .clientSecret(secretCipher.decrypt(provider.getClientSecretEncrypted()))
                .clientName(provider.getDisplayName())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                // Spring resolves this against the current request, so it works behind a proxy and
                // in dev without hard-coding a host.
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(scopes)
                .build();
    }
}
