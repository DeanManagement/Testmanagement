package com.deanmanagement.testmanagement.project.internal.dto.testrun;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

/** Bulk-set the status of multiple results within a run (PRD-008 §2.1). */
public record BulkResultStatusRequest(
        @NotEmpty Set<UUID> resultIds,
        @NotNull TestResultStatus status,
        boolean cascadeSteps,
        /* TM24: a project result status; when set, its category replaces status. */
        UUID customStatusId
) {
    public BulkResultStatusRequest(Set<UUID> resultIds, TestResultStatus status, boolean cascadeSteps) {
        this(resultIds, status, cascadeSteps, null);
    }
}
