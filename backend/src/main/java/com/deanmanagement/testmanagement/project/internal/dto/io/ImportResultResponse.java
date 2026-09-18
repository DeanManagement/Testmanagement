package com.deanmanagement.testmanagement.project.internal.dto.io;

import java.util.List;

/**
 * Result of a test-case import (or dry-run). {@code imported} counts rows that were (or would be)
 * created; {@code skipped} counts rows rejected with a row-level error.
 */
public record ImportResultResponse(
        int imported,
        int skipped,
        boolean dryRun,
        List<ImportError> errors,
        /* Rows imported with a change, e.g. ACTIVE turned into IN_REVIEW under review (PRD-033). */
        List<ImportError> warnings
) {
    public record ImportError(int row, String message) {
    }
}
