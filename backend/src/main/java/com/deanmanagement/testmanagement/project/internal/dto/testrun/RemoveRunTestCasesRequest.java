package com.deanmanagement.testmanagement.project.internal.dto.testrun;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * TM22: cases to take out of a run, every result of each (all its parameter sets). A result that
 * was already recorded is only discarded with {@code discardRecordedResults}; otherwise the call is
 * refused, so a recorded outcome is never lost by accident.
 */
public record RemoveRunTestCasesRequest(@NotNull @Size(min = 1, max = 200) Set<UUID> testCaseIds,
                                        Boolean discardRecordedResults) {

    public boolean discards() {
        return Boolean.TRUE.equals(discardRecordedResults);
    }
}
