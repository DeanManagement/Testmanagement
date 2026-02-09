package com.deanmanagement.testmanagement.dto;

import jakarta.validation.constraints.NotBlank;

public record TestStepRequest(
        @NotBlank String action,
        String expectedResult
) {
}
