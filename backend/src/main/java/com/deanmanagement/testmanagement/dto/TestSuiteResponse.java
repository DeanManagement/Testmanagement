package com.deanmanagement.testmanagement.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record TestSuiteResponse(
        UUID id,
        String name,
        String description,
        Set<TestCaseSummary> testCases,
        Instant createdAt,
        Instant updatedAt
) {
    public record TestCaseSummary(UUID id, String title) {}
}
