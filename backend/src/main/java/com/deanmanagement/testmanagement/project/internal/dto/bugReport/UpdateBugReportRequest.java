package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/** Edits a bug's content. Status is not here: it changes only through ChangeBugStatusRequest (PRD-045 §1). */
public record UpdateBugReportRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        String stepsToReproduce,
        String expectedBehavior,
        String actualBehavior,
        @NotNull Priority priority,
        String environment,
        UUID testResultId,
        UUID testRunId,
        UUID assigneeId,
        UUID environmentId,
        /* PRD-035: keyed by field name; see CustomFieldValueWriter for null and clearing rules. */
        Map<String, Object> customFields
) {
    public UpdateBugReportRequest(String title, String description, String stepsToReproduce,
                                  String expectedBehavior, String actualBehavior, Priority priority,
                                  String environment, UUID testResultId, UUID testRunId,
                                  UUID assigneeId, UUID environmentId) {
        this(title, description, stepsToReproduce, expectedBehavior, actualBehavior, priority, environment,
                testResultId, testRunId, assigneeId, environmentId, null);
    }
}
