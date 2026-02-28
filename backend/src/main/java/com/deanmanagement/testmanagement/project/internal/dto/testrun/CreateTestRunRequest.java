package com.deanmanagement.testmanagement.project.internal.dto.testrun;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record CreateTestRunRequest(
        @NotBlank @Size(max = 255) String name,
        String environment,
        Set<UUID> testCaseIds,
        UUID testPlanId,
        UUID executorId
) {
}
