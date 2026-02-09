package com.deanmanagement.testmanagement.project.internal.dto;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public record UpdateTestCaseRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        String preconditions,
        Priority priority,
        TestCaseStatus status,
        Set<String> labels,
        @Valid List<TestStepRequest> steps
) {
}
