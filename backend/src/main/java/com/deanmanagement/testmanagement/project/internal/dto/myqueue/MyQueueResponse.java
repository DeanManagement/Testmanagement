package com.deanmanagement.testmanagement.project.internal.dto.myqueue;

import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlanStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot of "what should I do next" for the calling user, surfaced on the
 * home dashboard. Each bucket is capped server-side (currently 5 items) so the
 * response always serialises to a small, predictable payload — the widget on
 * the dashboard does not paginate.
 *
 * The buckets are intentionally narrow and high-signal; a noisy queue would be
 * worse than no queue at all.
 */
public record MyQueueResponse(
        List<DueTestPlanItem> dueTestPlans,
        List<InProgressRunItem> inProgressRuns,
        List<StaleBugReportItem> staleBugReports,
        List<OldDraftTestCaseItem> oldDraftTestCases
) {

    /** A test plan I am assigned to that is due within the next week (or overdue). */
    public record DueTestPlanItem(
            UUID id,
            String name,
            UUID projectId,
            String projectKey,
            String projectName,
            TestPlanStatus status,
            LocalDate targetDate
    ) {}

    /** A test run I started and have not finished. */
    public record InProgressRunItem(
            UUID id,
            String key,
            String name,
            UUID projectId,
            String projectKey,
            String projectName,
            Instant updatedAt
    ) {}

    /** A bug report I filed that is still open and has not been touched in a week. */
    public record StaleBugReportItem(
            UUID id,
            String title,
            UUID projectId,
            String projectKey,
            String projectName,
            BugReportStatus status,
            Instant updatedAt
    ) {}

    /** A test case I authored that is still in DRAFT after two weeks. */
    public record OldDraftTestCaseItem(
            UUID id,
            String key,
            String title,
            UUID projectId,
            String projectKey,
            String projectName,
            Instant updatedAt
    ) {}
}
