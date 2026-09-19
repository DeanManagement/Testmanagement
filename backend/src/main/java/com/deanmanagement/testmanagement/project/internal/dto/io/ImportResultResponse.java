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
        List<ImportError> warnings,
        /* Gherkin only (PRD-040): @tm:-keyed scenarios that changed their case, and those that did not. */
        int updated,
        int unchanged
) {
    public ImportResultResponse(int imported, int skipped, boolean dryRun, List<ImportError> errors,
                                List<ImportError> warnings) {
        this(imported, skipped, dryRun, errors, warnings, 0, 0);
    }

    /** {@code row} is the 1-based row or scenario; 0 for a note about the whole upload. */
    public record ImportError(int row, String message) {
    }
}
