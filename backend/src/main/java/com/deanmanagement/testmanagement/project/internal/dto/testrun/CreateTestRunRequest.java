package com.deanmanagement.testmanagement.project.internal.dto.testrun;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record CreateTestRunRequest(
        @NotBlank @Size(max = 255) String name,
        String environment,
        Set<UUID> testCaseIds,
        UUID testPlanId,
        UUID executorId,
        UUID environmentId,
        @Size(max = MAX_ENVIRONMENTS) List<UUID> environmentIds,
        /* PRD-035: keyed by field name; see CustomFieldValueWriter for null and clearing rules. */
        Map<String, Object> customFields
) {
    /** Upper bound for {@code environmentIds}: one run per environment in a single request (PRD-032). */
    public static final int MAX_ENVIRONMENTS = 20;

    /** Single-run creation, the common case. */
    public CreateTestRunRequest(String name, String environment, Set<UUID> testCaseIds, UUID testPlanId,
                                UUID executorId) {
        this(name, environment, testCaseIds, testPlanId, executorId, null, null, null);
    }

    public CreateTestRunRequest(String name, String environment, Set<UUID> testCaseIds, UUID testPlanId,
                                UUID executorId, UUID environmentId, List<UUID> environmentIds) {
        this(name, environment, testCaseIds, testPlanId, executorId, environmentId, environmentIds, null);
    }
}
