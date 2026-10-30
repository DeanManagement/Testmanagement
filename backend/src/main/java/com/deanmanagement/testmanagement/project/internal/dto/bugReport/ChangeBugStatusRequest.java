package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.BugResolution;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * The one way a bug's status changes (PRD-045 §1). {@code resolution} is required for CLOSED,
 * optional for RESOLVED and refused otherwise; {@code duplicateOfId} goes with DUPLICATE.
 */
/**
 * {@code statusId} names the project's own status (TM47) and wins; with only {@code status}, the
 * bug lands on that category's first status.
 */
public record ChangeBugStatusRequest(
        BugReportStatus status,
        @NotBlank String reason,
        BugResolution resolution,
        UUID duplicateOfId,
        UUID statusId
) {
    public ChangeBugStatusRequest(BugReportStatus status, String reason, BugResolution resolution, UUID duplicateOfId) {
        this(status, reason, resolution, duplicateOfId, null);
    }

    public ChangeBugStatusRequest(BugReportStatus status, String reason) {
        this(status, reason, null, null, null);
    }
}
