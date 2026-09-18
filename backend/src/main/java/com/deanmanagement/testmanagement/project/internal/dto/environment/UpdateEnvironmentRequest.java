package com.deanmanagement.testmanagement.project.internal.dto.environment;

import jakarta.validation.constraints.Size;

/** Every field is optional; null leaves it unchanged. A blank description clears it. */
public record UpdateEnvironmentRequest(
        @Size(max = 255) String name,
        String description,
        Integer sortOrder,
        Boolean archived
) {
}
