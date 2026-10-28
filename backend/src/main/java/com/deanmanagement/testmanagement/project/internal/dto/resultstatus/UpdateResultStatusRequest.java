package com.deanmanagement.testmanagement.project.internal.dto.resultstatus;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Every field is optional; null leaves it unchanged. The category is fixed (TM24). */
public record UpdateResultStatusRequest(
        @Size(max = 100) String name,
        @Pattern(regexp = ResultStatusColor.PATTERN) String color,
        Integer position,
        Boolean archived
) {
}
