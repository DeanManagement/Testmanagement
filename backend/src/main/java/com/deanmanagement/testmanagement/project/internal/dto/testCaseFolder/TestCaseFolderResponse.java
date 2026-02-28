package com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TestCaseFolderResponse(
        UUID id,
        String name,
        UUID parentId,
        int sortOrder,
        long testCaseCount,
        List<TestCaseFolderResponse> children,
        Instant createdAt,
        Instant updatedAt
) {
}
