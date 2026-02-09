package com.deanmanagement.testmanagement.project.internal.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyCreatedResponse(
        UUID id,
        String name,
        String keyPrefix,
        String rawKey,
        Instant createdAt
) {
}
