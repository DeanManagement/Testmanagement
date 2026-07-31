package com.deanmanagement.testmanagement.project.internal.dto.version;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One historical state of a test case (PRD-011). */
public record TestCaseVersionResponse(
        UUID id,
        int versionNumber,
        Instant versionAt,
        String title,
        String description,
        String preconditions,
        Priority priority,
        TestCaseStatus status,
        List<String> labels,
        List<StepSnapshot> steps,
        UUID createdBy
) {
    public record StepSnapshot(
            int orderIndex,
            String action,
            String expectedResult,
            String testData
    ) {
    }
}
