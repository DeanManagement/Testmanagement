package com.deanmanagement.testmanagement.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String description,
        String key,
        Instant createdAt,
        Instant updatedAt
) {
}
