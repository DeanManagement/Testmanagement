package com.deanmanagement.testmanagement.user.internal.sso;

import java.util.List;

/**
 * What the login screen needs, served publicly: whether to show the password form and which SSO
 * buttons to render. Carries no secrets, issuer URLs or claim configuration — an unauthenticated
 * caller learns only that a provider called "Acme SSO" exists, which they would see on the button
 * anyway.
 */
public record AuthConfigResponse(
        boolean localLoginEnabled,
        List<AuthProviderSummary> providers
) {
    public record AuthProviderSummary(String slug, String displayName) {
    }
}
