package com.deanmanagement.testmanagement.dto;

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
