package com.deanmanagement.testmanagement.project.internal.dto.resultstatus;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** TM24: the category cannot change later, so it is chosen here. */
public record CreateResultStatusRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Pattern(regexp = ResultStatusColor.PATTERN) String color,
        @NotNull TestResultStatus category
) {
}
