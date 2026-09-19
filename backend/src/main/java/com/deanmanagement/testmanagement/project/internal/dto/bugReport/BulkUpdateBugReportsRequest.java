package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.BugResolution;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * One change applied to many bugs, all or nothing (PRD-045 §3.2). Only the fields given change.
 * {@code clearAssignee} unassigns; a status change follows the single-bug rules, reason included.
 */
public record BulkUpdateBugReportsRequest(
        @NotEmpty @Size(max = BulkUpdateBugReportsRequest.MAX_IDS) List<UUID> ids,
        UUID assigneeId,
        Boolean clearAssignee,
        Priority priority,
        BugReportStatus status,
        BugResolution resolution,
        UUID duplicateOfId,
        String reason
) {
    public static final int MAX_IDS = 100;
}
