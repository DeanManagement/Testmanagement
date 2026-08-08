package com.deanmanagement.testmanagement.project.internal.dto.buildserver;

import com.deanmanagement.testmanagement.project.internal.entity.BuildServerProviderType;

import java.time.Instant;
import java.util.UUID;

/**
 * A build-server connection as returned to the instance admin. There is deliberately no token
 * field of any kind — not even a masked one; {@code tokenSet} tells the UI whether to render
 * "replace token" or "add token".
 */
public record BuildServerConfigResponse(
        UUID id,
        String name,
        BuildServerProviderType provider,
        String baseUrl,
        boolean active,
        boolean tokenSet,
        String lastError,
        Instant lastErrorAt,
        Instant updatedAt
) {
}
