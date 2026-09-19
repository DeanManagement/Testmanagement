package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.BugResolution;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * The one way a bug's status changes (PRD-045 §1). {@code resolution} is required for CLOSED,
 * optional for RESOLVED and refused otherwise; {@code duplicateOfId} goes with DUPLICATE.
 */
public record ChangeBugStatusRequest(
        @NotNull BugReportStatus status,
        @NotBlank String reason,
        BugResolution resolution,
        UUID duplicateOfId
) {
    public ChangeBugStatusRequest(BugReportStatus status, String reason) {
        this(status, reason, null, null);
    }
}
