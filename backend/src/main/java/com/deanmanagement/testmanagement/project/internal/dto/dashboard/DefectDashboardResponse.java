package com.deanmanagement.testmanagement.project.internal.dto.dashboard;

import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The project's defects at a glance (PRD-047): how many are open, split by status and by priority,
 * and bugs reported versus resolved per week. Every status and priority is present, zero included.
 */
public record DefectDashboardResponse(
        long open,
        Map<BugReportStatus, Long> byStatus,
        Map<Priority, Long> openByPriority,
        List<Week> trend
) {
    /** A week starting Monday (UTC); resolved counts resolutions that still stand. */
    public record Week(LocalDate weekStart, long created, long resolved) {
    }
}
