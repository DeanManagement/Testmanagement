package com.deanmanagement.testmanagement.project.internal.dto.apiKey;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyCreatedResponse(
        UUID id,
        String name,
        String keyPrefix,
        String rawKey,
        Instant createdAt,
        UUID projectId,
        String projectName,
        ProjectRole role
) {
}
