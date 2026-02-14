package com.deanmanagement.testmanagement.project.internal.dto.testplan;

import com.deanmanagement.testmanagement.project.internal.entity.TestPlanStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateTestPlanRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        TestPlanStatus status,
        LocalDate targetDate
) {
}
