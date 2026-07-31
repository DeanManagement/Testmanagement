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
        List<ImportError> errors
) {
    public record ImportError(int row, String message) {
    }
}
