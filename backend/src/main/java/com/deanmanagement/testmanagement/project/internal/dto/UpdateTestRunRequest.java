package com.deanmanagement.testmanagement.project.internal.dto;

import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTestRunRequest(
        @NotBlank @Size(max = 255) String name,
        String environment,
        TestRunStatus status
) {
}
