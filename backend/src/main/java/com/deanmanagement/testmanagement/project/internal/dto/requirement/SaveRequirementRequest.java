package com.deanmanagement.testmanagement.project.internal.dto.requirement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveRequirementRequest(
        @NotBlank @Size(max = 100) String externalId,
        @NotBlank @Size(max = 500) String title,
        String description
) {
}
