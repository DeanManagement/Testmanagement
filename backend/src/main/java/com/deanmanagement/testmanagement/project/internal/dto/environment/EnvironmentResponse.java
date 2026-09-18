package com.deanmanagement.testmanagement.project.internal.dto.environment;

import java.util.UUID;

public record EnvironmentResponse(
        UUID id,
        String name,
        String description,
        int sortOrder,
        boolean archived,
        long runCount,
        long bugCount
) {
}
