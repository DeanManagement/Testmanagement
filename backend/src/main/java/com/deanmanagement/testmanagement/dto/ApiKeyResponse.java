package com.deanmanagement.testmanagement.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyResponse(
        UUID id,
        String name,
        String keyPrefix,
        boolean revoked,
        Instant lastUsedAt,
        Instant createdAt
) {
}
