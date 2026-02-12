package com.deanmanagement.testmanagement.project.internal.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
        @NotBlank
        @Size(max = 255)
        String name,

        String description
) {
}
