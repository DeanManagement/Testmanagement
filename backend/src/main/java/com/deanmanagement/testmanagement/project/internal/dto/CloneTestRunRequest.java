package com.deanmanagement.testmanagement.project.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CloneTestRunRequest(
        @NotBlank @Size(max = 255) String name,
        String environment
) {
}
