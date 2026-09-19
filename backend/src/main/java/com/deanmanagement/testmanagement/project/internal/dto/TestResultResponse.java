package com.deanmanagement.testmanagement.project.internal.dto;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TestResultResponse(
        UUID id,
        UUID testCaseId,
        String testCaseTitle,
        TestResultStatus status,
        String comment,
        String defectLink,
        /** Which version of the test case this executed (PRD-011); null for pre-versioning results. */
        Integer executedVersion,
        /** Which parameter set this executed (PRD-015); null for an ordinary case. */
        String parameterSetName,
        List<StepResultResponse> stepResults,
        /** When it left PENDING, and the measured effort (PRD-036); both null when unknown. */
        Instant executedAt,
        Long durationMs,
        /** The case's live estimate (PRD-036), so a client can keep "remaining" current as it records. */
        Integer estimateMinutes,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy
) {
}
