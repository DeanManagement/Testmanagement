package com.deanmanagement.testmanagement.project.internal.dto.testCase;

import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record BulkStatusRequest(
        @NotNull @Size(min = 1, max = 100) Set<UUID> testCaseIds,
        @NotNull TestCaseStatus status
) {
}
