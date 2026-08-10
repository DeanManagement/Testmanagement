package com.deanmanagement.testmanagement.project.internal.dto.apiKey;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyResponse(
        UUID id,
        String name,
        String keyPrefix,
        boolean revoked,
        Instant lastUsedAt,
        Instant createdAt,
        // PRD: null means the key still carries the secret it was issued with.
        Instant rotatedAt,
        // PRD-021 §4.2: null for legacy/global keys.
        UUID projectId,
        String projectName,
        // PRD-025 §3.2: the key's role on its project.
        ProjectRole role
) {
}
