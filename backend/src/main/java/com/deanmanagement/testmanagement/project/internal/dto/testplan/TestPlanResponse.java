package com.deanmanagement.testmanagement.project.internal.dto.testplan;

import com.deanmanagement.testmanagement.project.internal.entity.TestPlanStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TestPlanResponse(
        UUID id,
        String name,
        String description,
        TestPlanStatus status,
        LocalDate targetDate,
        int testRunCount,
        UUID assigneeId,
        String assigneeName,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy
) {
}
