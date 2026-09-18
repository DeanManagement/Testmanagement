package com.deanmanagement.testmanagement.project.internal.dto.project;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String description,
        String key,
        boolean bugReportsEnabled,
        boolean reviewRequired,
        ProjectRole reviewerMinRole,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy
) {
}
