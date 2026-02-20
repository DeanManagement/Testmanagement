package com.deanmanagement.testmanagement.project.internal.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record TestSuiteResponse(
        UUID id,
        String name,
        String description,
        Set<TestCaseSummary> testCases,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy
) {
    public record TestCaseSummary(UUID id, String title) {}
}
