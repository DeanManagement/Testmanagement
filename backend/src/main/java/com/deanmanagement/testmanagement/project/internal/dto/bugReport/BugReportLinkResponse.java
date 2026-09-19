package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

import java.time.Instant;
import java.util.UUID;

/** A further occurrence of a bug (PRD-047): the run, result and, if named, step it showed up in. */
public record BugReportLinkResponse(
        UUID id,
        UUID testRunId,
        String testRunKey,
        String testRunName,
        UUID testResultId,
        UUID testCaseId,
        String testCaseKey,
        UUID stepResultId,
        /* 1-based, as the execution view numbers steps; null when no step was named. */
        Integer stepNumber,
        Instant createdAt
) {
}
