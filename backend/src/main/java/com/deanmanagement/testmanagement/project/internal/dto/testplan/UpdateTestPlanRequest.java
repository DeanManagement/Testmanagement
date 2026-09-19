package com.deanmanagement.testmanagement.project.internal.dto.testplan;

import com.deanmanagement.testmanagement.project.internal.entity.TestPlanStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateTestPlanRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        TestPlanStatus status,
        LocalDate targetDate,
        UUID assigneeId,
        /* PRD-037: null leaves the gate as it is; a null threshold inside it switches that one off. */
        @Valid ReleaseGate gate
) {
    public UpdateTestPlanRequest(String name, String description, TestPlanStatus status, LocalDate targetDate,
                                 UUID assigneeId) {
        this(name, description, status, targetDate, assigneeId, null);
    }
}
