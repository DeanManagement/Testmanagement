package com.deanmanagement.testmanagement.project.internal.dto.requirement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RequirementResponse(
        UUID id,
        String externalId,
        String title,
        String description,
        List<LinkedTestCase> testCases,
        Instant createdAt,
        Instant updatedAt
) {
    public record LinkedTestCase(UUID id, String key, String title) {
    }
}
