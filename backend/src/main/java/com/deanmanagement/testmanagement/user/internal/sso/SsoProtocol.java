package com.deanmanagement.testmanagement.user.internal.sso;

/**
 * Which authentication protocol a configured provider speaks.
 *
 * <p>The distinction is not cosmetic. {@link #OIDC} providers hand back a signed ID token whose
 * claims <em>are</em> the identity, so one code path serves every one of them. {@link #GITHUB} has
 * no ID token and no discovery document — its user-facing sign-in is plain OAuth 2.0 — so the
 * identity has to be fetched from its API afterwards and assembled into the same shape.
 *
 * <p>Deliberately not a general "OAUTH2" value. A generic OAuth 2.0 provider tells you nothing
 * about where the user's identity lives; every one needs its own knowledge of which endpoint to
 * call and what the response looks like. Naming the vendor keeps that honest and makes adding the
 * next one an explicit decision rather than a configuration screen no one can fill in correctly.
 */
public enum SsoProtocol {

    /** Anything with a {@code /.well-known/openid-configuration} — Keycloak, Entra ID, GitLab. */
    OIDC,

    /** github.com or GitHub Enterprise Server. */
    GITHUB;

    public boolean isOidc() {
        return this == OIDC;
    }
}
