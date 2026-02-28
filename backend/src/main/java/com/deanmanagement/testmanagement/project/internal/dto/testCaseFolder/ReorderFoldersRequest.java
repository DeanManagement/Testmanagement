package com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record ReorderFoldersRequest(
        @NotEmpty @Valid List<FolderOrder> folders
) {
    public record FolderOrder(
            UUID id,
            UUID parentId,
            int sortOrder
    ) {
    }
}
