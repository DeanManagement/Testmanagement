package com.deanmanagement.testmanagement.project.internal.dto;

import java.util.UUID;

/**
 * A step of a test case: either its own text, or with {@code sharedStepId} a reference to a shared
 * block of the same project (PRD-030), in which case the other fields are ignored. Which of the two
 * is required is checked by TestCaseService, since a bean constraint cannot express "one of".
 *
 * <p>{@code id} names the existing step this one is, so its image follows it when steps are
 * reordered, inserted or removed (TM06). Optional: without ids, images stay with positions.
 */
public record TestStepRequest(
        String action,
        String expectedResult,
        String testData,
        UUID sharedStepId,
        UUID id
) {
    public TestStepRequest(String action, String expectedResult, String testData) {
        this(action, expectedResult, testData, null, null);
    }

    public TestStepRequest(String action, String expectedResult, String testData, UUID sharedStepId) {
        this(action, expectedResult, testData, sharedStepId, null);
    }
}
