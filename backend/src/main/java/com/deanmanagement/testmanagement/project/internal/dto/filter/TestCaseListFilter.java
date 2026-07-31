package com.deanmanagement.testmanagement.project.internal.dto.filter;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Filter criteria for the test-case list endpoint. All fields are optional; a {@code null} or empty
 * value means "no constraint".
 */
public record TestCaseListFilter(
        String q,
        List<TestCaseStatus> status,
        List<Priority> priority,
        List<String> label,
        UUID folderId,
        boolean rootOnly,
        Instant updatedAfter
) {
}
