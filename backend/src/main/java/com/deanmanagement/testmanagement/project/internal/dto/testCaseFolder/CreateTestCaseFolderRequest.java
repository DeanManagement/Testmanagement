package com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateTestCaseFolderRequest(
        @NotBlank @Size(max = 255) String name,
        UUID parentId
) {
}
