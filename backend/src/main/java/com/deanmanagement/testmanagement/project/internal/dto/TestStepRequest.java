package com.deanmanagement.testmanagement.project.internal.dto;

import java.util.UUID;

/**
 * A step of a test case: either its own text, or with {@code sharedStepId} a reference to a shared
 * block of the same project (PRD-030), in which case the other fields are ignored. Which of the two
 * is required is checked by TestCaseService, since a bean constraint cannot express "one of".
 */
public record TestStepRequest(
        String action,
        String expectedResult,
        String testData,
        UUID sharedStepId
) {
    public TestStepRequest(String action, String expectedResult, String testData) {
        this(action, expectedResult, testData, null);
    }
}
