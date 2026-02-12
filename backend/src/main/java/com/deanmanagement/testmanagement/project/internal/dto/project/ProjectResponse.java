package com.deanmanagement.testmanagement.project.internal.dto.project;

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
