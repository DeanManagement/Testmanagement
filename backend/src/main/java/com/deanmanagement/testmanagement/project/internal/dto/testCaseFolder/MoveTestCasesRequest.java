package com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record MoveTestCasesRequest(
        @NotEmpty List<UUID> testCaseIds,
        UUID targetFolderId
) {
}
