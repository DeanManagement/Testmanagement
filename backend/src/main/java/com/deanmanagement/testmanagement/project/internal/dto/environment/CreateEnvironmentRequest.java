package com.deanmanagement.testmanagement.project.internal.dto.environment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEnvironmentRequest(
        @NotBlank @Size(max = 255) String name,
        String description
) {
}
