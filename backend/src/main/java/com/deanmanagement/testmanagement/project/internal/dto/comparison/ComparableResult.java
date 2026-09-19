package com.deanmanagement.testmanagement.project.internal.dto.comparison;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;

import java.util.UUID;

/**
 * The slice of a test result a run comparison needs (PRD-038 §3.2), queried as a projection so a
 * comparison never loads step results or screenshots.
 *
 * @param parameterSetName null for an ordinary case; a parameterized case has one result per set
 */
public record ComparableResult(
        UUID resultId,
        UUID testCaseId,
        String testCaseKey,
        String title,
        String parameterSetName,
        TestResultStatus status,
        Integer executedVersion
) {
}
