package com.deanmanagement.testmanagement.project.internal.dto.testSuite;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record BulkTestCasesRequest(
        @NotNull @Size(min = 1, max = 100) Set<UUID> testCaseIds
) {
}
