package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateBugReportRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        String stepsToReproduce,
        String expectedBehavior,
        String actualBehavior,
        @NotNull Priority priority,
        String environment,
        UUID testResultId,
        UUID testRunId,
        UUID assigneeId
) {
}
