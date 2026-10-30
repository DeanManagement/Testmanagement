package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The bug list's query parameters, shared by the list and its CSV export (TM48). {@code assignee}
 * takes user ids, {@code none} and {@code me}; {@code reporter} user ids; dates are ISO days.
 */
public record BugReportListParams(
        String q,
        List<BugReportStatus> status,
        List<Priority> priority,
        List<String> assignee,
        UUID testResultId,
        UUID environmentId,
        List<UUID> reporter,
        UUID testRunId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
        List<String> label
) {
    private static final String UNASSIGNED = "none";
    private static final String ME = "me";

    /** {@code actor} stands in for {@code me}. */
    public BugReportFilter toFilter(UUID actor) {
        List<String> assignees = assignee == null ? List.of() : assignee;
        List<UUID> assigneeIds = assignees.stream()
                .filter(a -> !UNASSIGNED.equals(a))
                .map(a -> ME.equals(a) ? actor : parseUserId(a))
                .toList();
        return new BugReportFilter(q, status, priority, assigneeIds, assignees.contains(UNASSIGNED), testResultId,
                environmentId, reporter == null ? List.of() : reporter, testRunId, createdFrom, createdTo,
                label == null ? List.of() : label);
    }

    private static UUID parseUserId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            throw new IllegalArgumentException("assignee must be a user id, 'none' or 'me', not '" + value + "'");
        }
    }
}
