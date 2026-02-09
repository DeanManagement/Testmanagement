package com.deanmanagement.testmanagement.dto;

import com.deanmanagement.testmanagement.entity.Priority;
import com.deanmanagement.testmanagement.entity.TestCaseStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public record CreateTestCaseRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        String preconditions,
        @NotNull Priority priority,
        @NotNull TestCaseStatus status,
        Set<String> labels,
        @Valid List<TestStepRequest> steps
) {
}
