package com.deanmanagement.testmanagement.user.internal.sso;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create or update a provider. {@code clientSecret} is write-only and optional on update — omitting
 * it keeps the stored secret, so an admin can retune claim mappings without re-fetching the secret
 * from their IdP.
 */
public record SaveSsoProviderRequest(
        /* Lowercase, URL-safe: it appears in /oauth2/authorization/{slug} and the callback path. */
        @NotBlank @Size(max = 50) @Pattern(regexp = "[a-z0-9][a-z0-9-]*",
                message = "Slug may contain only lowercase letters, digits and hyphens")
        String slug,
        @NotBlank @Size(max = 100) String displayName,
        /* Null means OIDC, so a client written against the pre-GitHub API keeps working. */
        SsoProtocol protocol,
        /* The OIDC discovery root, or for GitHub the instance root. */
        @NotBlank @Size(max = 500) String issuerUri,
        @NotBlank @Size(max = 300) String clientId,
        @Size(max = 500) String clientSecret,
        @Size(max = 300) String scopes,
        @Size(max = 100) String emailClaim,
        @Size(max = 100) String nameClaim,
        @Size(max = 100) String adminClaim,
        @Size(max = 200) String adminClaimValue,
        Boolean trustEmailForLinking,
        Boolean autoProvision,
        Boolean active
) {
}
