package com.deanmanagement.testmanagement.project.internal.dto.filter;

/**
 * Filter criteria for the test-suite list endpoint. All fields are optional.
 */
public record TestSuiteListFilter(
        String q
) {
}
