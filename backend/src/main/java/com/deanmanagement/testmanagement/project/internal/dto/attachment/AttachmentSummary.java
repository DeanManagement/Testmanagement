package com.deanmanagement.testmanagement.project.internal.dto.attachment;

import java.time.Instant;
import java.util.UUID;

/** An attachment without its bytes: what lists, exports and MCP show (PRD-044). */
public record AttachmentSummary(
        UUID id,
        UUID testCaseId,
        String fileName,
        String contentType,
        long sizeBytes,
        String sha256,
        Instant createdAt,
        UUID createdBy
) {
}
