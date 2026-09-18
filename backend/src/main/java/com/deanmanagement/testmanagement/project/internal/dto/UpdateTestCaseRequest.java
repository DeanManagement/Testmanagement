package com.deanmanagement.testmanagement.project.internal.dto;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record UpdateTestCaseRequest(
        /*
         * Nullable since PRD-025: null means "leave the current title alone", which is what makes
         * a partial update expressible. Blank is still refused — a test case must have a title —
         * but that check moved to TestCaseService.update, because @NotBlank here would also reject
         * the legitimate null.
         */
        @Size(max = 255) String title,
        String description,
        String preconditions,
        Priority priority,
        TestCaseStatus status,
        Set<String> labels,
        @Valid List<TestStepRequest> steps,
        /* PRD-035: keyed by field name; see CustomFieldValueWriter for null and clearing rules. */
        Map<String, Object> customFields
) {
    public UpdateTestCaseRequest(String title, String description, String preconditions, Priority priority,
                                 TestCaseStatus status, Set<String> labels, List<TestStepRequest> steps) {
        this(title, description, preconditions, priority, status, labels, steps, null);
    }
}
