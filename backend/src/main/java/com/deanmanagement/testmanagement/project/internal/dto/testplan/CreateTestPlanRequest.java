package com.deanmanagement.testmanagement.project.internal.dto.testplan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateTestPlanRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        LocalDate targetDate,
        UUID assigneeId
) {
}
