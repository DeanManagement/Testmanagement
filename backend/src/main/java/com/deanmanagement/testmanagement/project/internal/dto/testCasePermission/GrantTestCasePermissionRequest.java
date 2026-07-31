package com.deanmanagement.testmanagement.project.internal.dto.testCasePermission;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GrantTestCasePermissionRequest(
        @NotNull UUID userId,
        /* Boxed, not primitive: Jackson rejects a record whose primitive component is absent from
         * the body, and omitting canEdit should mean view-only rather than a 400. */
        Boolean canEdit
) {
    public boolean isCanEdit() {
        return Boolean.TRUE.equals(canEdit);
    }
}
