package com.deanmanagement.testmanagement.project.internal.dto.comparison;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Two runs of one project side by side (PRD-038): every (test case, parameter set) classified by
 * how its result moved from {@code base} to {@code head}. Computed on read; nothing is stored.
 *
 * @param baseAutoSelected true when the caller named only the head and the base was picked for it
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RunComparisonResponse(
        RunRef base,
        RunRef head,
        boolean baseAutoSelected,
        Counts counts,
        List<Row> rows
) {
    /** In display order: what a reader looks for first comes first. */
    public enum Category { NEWLY_FAILING, FIXED, STILL_FAILING, ADDED, REMOVED, OTHER_CHANGE, UNCHANGED }

    /** @param happenedAt when the run happened: end, else start, else creation time */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RunRef(UUID id, String key, String name, @Nullable String environment, TestRunStatus status,
                         @Nullable Instant endTime, Instant happenedAt) {
    }

    public record Counts(int newlyFailing, int fixed, int stillFailing, int added, int removed, int otherChange,
                         int unchanged) {
    }

    /**
     * @param baseStatus     null when the key is only in the head (ADDED)
     * @param headStatus     null when the key is only in the base (REMOVED)
     * @param versionChanged the two results executed different versions of the case's wording
     *                       (PRD-011); false when either version is unknown
     * @param duplicates     results beyond one per run that were collapsed into this row, both runs
     *                       together; the worst status of each run is the one shown
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Row(Category category, UUID testCaseId, String testCaseKey, String title,
                      @Nullable String parameterSetName, @Nullable TestResultStatus baseStatus,
                      @Nullable TestResultStatus headStatus, @Nullable UUID baseResultId,
                      @Nullable UUID headResultId, boolean versionChanged, int duplicates) {
    }
}
