package com.deanmanagement.testmanagement.dto;

import com.deanmanagement.testmanagement.entity.TestRunStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTestRunRequest(
        @NotBlank @Size(max = 255) String name,
        String environment,
        TestRunStatus status
) {
}
