package com.deanmanagement.testmanagement.project.internal.dto.testCase;

import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record CreateTestCaseRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        String preconditions,
        @NotNull Priority priority,
        @NotNull TestCaseStatus status,
        Set<String> labels,
        @Valid List<TestStepRequest> steps,
        UUID folderId
) {
}
