package com.deanmanagement.testmanagement.project.internal.dto;

import jakarta.validation.constraints.NotBlank;

public record TestStepRequest(
        @NotBlank String action,
        String expectedResult
) {
}
