package com.deanmanagement.testmanagement.project.internal.dto.io;

import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.parameter.SaveParameterSetRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;

import java.util.List;
import java.util.Set;

/**
 * One scenario read as a test case, for the form's "Edit as Gherkin" (PRD-040 §3.7). Nothing is
 * written. {@code problems} are what an import would refuse; {@code warnings} what it would change.
 */
public record GherkinPreviewResponse(
        String key,
        String title,
        String description,
        String preconditions,
        /* Null when the scenario has no @priority: tag. */
        Priority priority,
        Set<String> labels,
        List<TestStepRequest> steps,
        List<SaveParameterSetRequest> parameterSets,
        List<String> problems,
        List<String> warnings
) {
}
