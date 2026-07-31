package com.deanmanagement.testmanagement.project.internal.dto.testCasePermission;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GrantTestCasePermissionRequest(
        @NotNull UUID userId,
        boolean canEdit
) {
}
