package com.deanmanagement.testmanagement.user.internal.sso;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deployment-level knobs for SSO (PRD-012). Provider details live in the database; only things an
 * operator rather than an admin decides belong here.
 */
@ConfigurationProperties(prefix = "app.sso")
public record SsoProperties(
        /*
         * Where the browser is sent after a successful SSO login. Must come from configuration,
         * never from a request parameter: the callback carries a freshly minted session token, so a
         * caller-controlled redirect would be a token-exfiltration hole.
         */
        String frontendCallbackUrl,
        /* Allow issuers on loopback / private addresses. Off by default (SSRF guard). */
        boolean allowPrivateIssuers,
        /* Require https issuer URLs. On by default; only disabled for local testing. */
        Boolean requireHttps
) {
    public SsoProperties {
        if (requireHttps == null) requireHttps = true;
        if (frontendCallbackUrl == null || frontendCallbackUrl.isBlank()) {
            frontendCallbackUrl = "/login/callback";
        }
    }
}
