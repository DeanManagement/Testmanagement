package com.deanmanagement.testmanagement.project.internal.ci;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;

import java.util.List;

/**
 * Normalized representation of a single executed test parsed from a CI report (JUnit XML or
 * Cucumber JSON). {@code steps} is populated for step-structured reports (Cucumber) and empty
 * otherwise.
 */
public record CiResult(
        String suiteName,
        String title,
        TestResultStatus status,
        String message,
        List<CiStep> steps
) {
    public record CiStep(String name, TestResultStatus status) {
    }
}
