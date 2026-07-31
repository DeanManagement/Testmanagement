package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.myqueue.MyQueueResponse;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlanStatus;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestPlanRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Builds the "My queue" snapshot rendered on the dashboard. Reads from four
 * existing tables — there is no new persistence here. Each bucket is bounded
 * so the response stays small (a hard cap of {@link #BUCKET_LIMIT} items per
 * category).
 *
 * <p>Clock is injected so tests can pin "now" — staleness thresholds are
 * relative to wall time, which would otherwise be a pain to assert against.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyQueueService {

    /** Hard cap per bucket. Larger numbers turn the widget into a backlog. */
    private static final int BUCKET_LIMIT = 5;

    /** A test plan is "due" when its targetDate is within the next 7 days (or already past). */
    private static final int TEST_PLAN_DUE_WINDOW_DAYS = 7;

    /** Bug reports filed by the user and not updated in this long are surfaced as stale. */
    private static final Duration BUG_REPORT_STALE_AFTER = Duration.ofDays(7);

    /** DRAFT test cases authored by the user and not updated in this long are surfaced. */
    private static final Duration DRAFT_TEST_CASE_STALE_AFTER = Duration.ofDays(14);

    private final TestPlanRepository testPlanRepository;
    private final TestRunRepository testRunRepository;
    private final BugReportRepository bugReportRepository;
    private final TestCaseRepository testCaseRepository;
    private final Clock clock;

    public MyQueueResponse buildFor(UUID userId) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        PageRequest limit = PageRequest.of(0, BUCKET_LIMIT);

        List<MyQueueResponse.DueTestPlanItem> dueTestPlans = testPlanRepository
                .findDueByAssignee(
                        userId,
                        EnumSet.of(TestPlanStatus.OPEN, TestPlanStatus.IN_PROGRESS),
                        today.plusDays(TEST_PLAN_DUE_WINDOW_DAYS),
                        limit)
                .stream()
                .map(tp -> new MyQueueResponse.DueTestPlanItem(
                        tp.getId(),
                        tp.getName(),
                        tp.getProject().getId(),
                        tp.getProject().getKey(),
                        tp.getProject().getName(),
                        tp.getStatus(),
                        tp.getTargetDate()))
                .toList();

        List<MyQueueResponse.InProgressRunItem> inProgressRuns = testRunRepository
                .findInProgressByExecutor(userId, limit)
                .stream()
                .map(run -> new MyQueueResponse.InProgressRunItem(
                        run.getId(),
                        run.getKey(),
                        run.getName(),
                        run.getProject().getId(),
                        run.getProject().getKey(),
                        run.getProject().getName(),
                        run.getUpdatedAt()))
                .toList();

        List<MyQueueResponse.StaleBugReportItem> staleBugReports = bugReportRepository
                .findStaleByCreatedBy(
                        userId,
                        EnumSet.of(BugReportStatus.OPEN, BugReportStatus.IN_PROGRESS),
                        now.minus(BUG_REPORT_STALE_AFTER),
                        limit)
                .stream()
                .map(b -> new MyQueueResponse.StaleBugReportItem(
                        b.getId(),
                        b.getTitle(),
                        b.getProject().getId(),
                        b.getProject().getKey(),
                        b.getProject().getName(),
                        b.getStatus(),
                        b.getUpdatedAt()))
                .toList();

        List<MyQueueResponse.OldDraftTestCaseItem> oldDraftTestCases = testCaseRepository
                .findStaleByCreatedByAndStatus(
                        userId,
                        TestCaseStatus.DRAFT,
                        now.minus(DRAFT_TEST_CASE_STALE_AFTER),
                        limit)
                .stream()
                .map(tc -> new MyQueueResponse.OldDraftTestCaseItem(
                        tc.getId(),
                        tc.getKey(),
                        tc.getTitle(),
                        tc.getProject().getId(),
                        tc.getProject().getKey(),
                        tc.getProject().getName(),
                        tc.getUpdatedAt()))
                .toList();

        return new MyQueueResponse(dueTestPlans, inProgressRuns, staleBugReports, oldDraftTestCases);
    }
}
