package com.deanmanagement.testmanagement.project.internal.dto;

import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * @param name lost its {@code @NotBlank} in PRD-027 §3.2: null now means "leave the name alone",
 *             so a caller changing only the status cannot revert a concurrent rename. Blank is
 *             still refused — by {@code TestRunService.update}, which can tell the two apart.
 *             Same change, for the same reason, as PRD-025 §8 made to {@code UpdateTestCaseRequest}
 */
public record UpdateTestRunRequest(
        @Size(max = 255) String name,
        String environment,
        TestRunStatus status,
        String reopenReason,
        UUID testPlanId,
        UUID environmentId,
        /* PRD-035: keyed by field name; see CustomFieldValueWriter for null and clearing rules. */
        Map<String, Object> customFields
) {
    public UpdateTestRunRequest(String name, String environment, TestRunStatus status, String reopenReason,
                                UUID testPlanId) {
        this(name, environment, status, reopenReason, testPlanId, null, null);
    }

    public UpdateTestRunRequest(String name, String environment, TestRunStatus status, String reopenReason,
                                UUID testPlanId, UUID environmentId) {
        this(name, environment, status, reopenReason, testPlanId, environmentId, null);
    }
}
