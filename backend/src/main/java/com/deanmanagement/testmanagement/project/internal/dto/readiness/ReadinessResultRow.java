package com.deanmanagement.testmanagement.project.internal.dto.readiness;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One result of a plan's non-aborted runs, as the readiness gate needs it (PRD-037 §3.2).
 *
 * @param runAt          when its run happened: end, else start, else creation time, the ordering
 *                       PRD-016 settled on so a backfilled CI run sorts by when it ran
 * @param resultUpdatedAt breaks ties within one run, where a case was recorded twice
 */
public record ReadinessResultRow(
        UUID testCaseId,
        String parameterSetName,
        TestResultStatus status,
        Instant runAt,
        Instant resultUpdatedAt
) {
}
