package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;

import java.time.LocalDate;
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
        UUID environmentId,
        /* TM48: who filed it (createdBy). */
        List<UUID> reporterIds,
        /* TM48: found in this run, or linked to one of its results. */
        UUID testRunId,
        /* TM48: filed on or after / on or before these days, in UTC. */
        LocalDate createdFrom,
        LocalDate createdTo,
        /* TM49/TM48: has any of these labels. */
        List<String> labels,
        /* TM47: in one of these project statuses; status above filters by category. */
        List<UUID> statusIds
) {
    public BugReportFilter(String q, List<BugReportStatus> status, List<Priority> priority, List<UUID> assigneeIds,
                           boolean includeUnassigned, UUID testResultId, UUID environmentId) {
        this(q, status, priority, assigneeIds, includeUnassigned, testResultId, environmentId,
                List.of(), null, null, null, List.of(), List.of());
    }

    public static BugReportFilter none() {
        return new BugReportFilter(null, List.of(), List.of(), List.of(), false, null, null);
    }

    public boolean filtersAssignee() {
        return includeUnassigned || (assigneeIds != null && !assigneeIds.isEmpty());
    }
}
