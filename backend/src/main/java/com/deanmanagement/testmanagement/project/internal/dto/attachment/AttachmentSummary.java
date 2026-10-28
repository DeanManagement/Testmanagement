package com.deanmanagement.testmanagement.project.internal.dto.attachment;

import java.time.Instant;
import java.util.UUID;

/**
 * An attachment without its bytes: what lists, exports and MCP show (PRD-044). Exactly one owner id
 * is set: a test case, a bug report (PRD-051), a test step or a step result's evidence (TM05).
 */
public record AttachmentSummary(
        UUID id,
        UUID testCaseId,
        UUID bugReportId,
        UUID testStepId,
        UUID stepResultId,
        String fileName,
        String contentType,
        long sizeBytes,
        String sha256,
        Instant createdAt,
        UUID createdBy
) {
}
