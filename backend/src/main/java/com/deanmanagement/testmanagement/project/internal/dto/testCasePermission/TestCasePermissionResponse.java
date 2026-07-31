package com.deanmanagement.testmanagement.project.internal.dto.testCasePermission;

import java.time.Instant;
import java.util.UUID;

public record TestCasePermissionResponse(
        UUID id,
        UUID testCaseId,
        UUID userId,
        String userDisplayName,
        boolean canEdit,
        Instant createdAt,
        Instant updatedAt
) {
}
