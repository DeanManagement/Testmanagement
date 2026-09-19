package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;

import java.util.List;
import java.util.UUID;

/**
 * The bug list's filters (PRD-045 §3.2). Empty lists and nulls mean "any". A bug matches the
 * assignee filter when it is assigned to one of {@code assigneeIds}, or unassigned and
 * {@code includeUnassigned} is set.
 */
public record BugReportFilter(
        String q,
        List<BugReportStatus> status,
        List<Priority> priority,
        List<UUID> assigneeIds,
        boolean includeUnassigned,
        UUID testResultId,
        UUID environmentId
) {
    public static BugReportFilter none() {
        return new BugReportFilter(null, List.of(), List.of(), List.of(), false, null, null);
    }

    public boolean filtersAssignee() {
        return includeUnassigned || (assigneeIds != null && !assigneeIds.isEmpty());
    }
}
