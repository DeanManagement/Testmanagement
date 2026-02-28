package com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTestCaseFolderRequest(
        @NotBlank @Size(max = 255) String name
) {
}
