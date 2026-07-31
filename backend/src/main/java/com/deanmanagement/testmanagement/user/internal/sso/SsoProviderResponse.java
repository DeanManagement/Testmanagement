package com.deanmanagement.testmanagement.user.internal.sso;

import java.time.Instant;
import java.util.UUID;

/**
 * A provider as returned to admins. There is no client-secret field of any kind, not even masked;
 * {@code secretSet} tells the UI whether to offer "replace" or "add".
 */
public record SsoProviderResponse(
        UUID id,
        String slug,
        String displayName,
        String issuerUri,
        String clientId,
        boolean secretSet,
        String scopes,
        String emailClaim,
        String nameClaim,
        String adminClaim,
        String adminClaimValue,
        boolean trustEmailForLinking,
        boolean autoProvision,
        boolean active,
        String lastError,
        Instant lastErrorAt,
        Instant updatedAt
) {
}
