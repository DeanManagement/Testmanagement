package com.deanmanagement.testmanagement.project.internal.dto.analytics;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One terminal result, projected for flakiness scoring (PRD-016).
 *
 * <p>A projection rather than the entity: scoring needs three columns per row and pulling
 * {@code TestResult} graphs would drag in steps and screenshots for no benefit.
 */
public record FlakyResultRow(
        UUID testCaseId,
        String testCaseKey,
        String testCaseTitle,
        TestResultStatus status,
        /** When the run this result belongs to happened; the ordering key for transitions. */
        Instant occurredAt
) {
}
