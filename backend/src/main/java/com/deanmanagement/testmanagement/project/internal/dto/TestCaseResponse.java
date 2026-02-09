package com.deanmanagement.testmanagement.project.internal.dto;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record TestCaseResponse(
        UUID id,
        String key,
        String title,
        String description,
        String preconditions,
        Priority priority,
        TestCaseStatus status,
        Set<String> labels,
        List<TestStepResponse> steps,
        Instant createdAt,
        Instant updatedAt
) {
}
