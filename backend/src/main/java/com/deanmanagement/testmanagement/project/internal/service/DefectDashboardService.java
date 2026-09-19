package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.dashboard.DefectDashboardResponse;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The dashboard's defect tile and charts (PRD-047). A separate endpoint, so the main dashboard stays
 * cheap for projects without bug reports. The trend reads {@code created_at} and {@code resolved_at}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefectDashboardService {

    static final int TREND_WEEKS = 12;

    private final BugReportService bugReportService;
    private final BugReportRepository bugReportRepository;
    private final Clock clock;

    public DefectDashboardResponse defects(UUID projectId) {
        bugReportService.requireBugReportsEnabled(projectId);
        Map<BugReportStatus, Long> byStatus = new EnumMap<>(BugReportStatus.class);
        for (BugReportStatus status : BugReportStatus.values()) {
            byStatus.put(status, 0L);
        }
        bugReportRepository.countByStatus(projectId)
                .forEach(row -> byStatus.put((BugReportStatus) row[0], (Long) row[1]));

        Map<Priority, Long> openByPriority = new EnumMap<>(Priority.class);
        for (Priority priority : Priority.values()) {
            openByPriority.put(priority, 0L);
        }
        bugReportRepository.countByPriorityAndStatusIn(projectId, BugReportStatus.OPEN_STATUSES)
                .forEach(row -> openByPriority.put((Priority) row[0], (Long) row[1]));

        long open = BugReportStatus.OPEN_STATUSES.stream().mapToLong(byStatus::get).sum();
        return new DefectDashboardResponse(open, byStatus, openByPriority, trend(projectId));
    }

    /** The last {@value #TREND_WEEKS} weeks, oldest first, the current one included and empty weeks too. */
    private List<DefectDashboardResponse.Week> trend(UUID projectId) {
        LocalDate thisWeek = LocalDate.now(clock.withZone(ZoneOffset.UTC))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate firstWeek = thisWeek.minusWeeks(TREND_WEEKS - 1L);
        Instant since = firstWeek.atStartOfDay(ZoneOffset.UTC).toInstant();

        long[] created = bucket(bugReportRepository.findCreatedAtSince(projectId, since), firstWeek);
        long[] resolved = bucket(bugReportRepository.findResolvedAtSince(projectId, since), firstWeek);
        List<DefectDashboardResponse.Week> weeks = new ArrayList<>(TREND_WEEKS);
        for (int i = 0; i < TREND_WEEKS; i++) {
            weeks.add(new DefectDashboardResponse.Week(firstWeek.plusWeeks(i), created[i], resolved[i]));
        }
        return weeks;
    }

    private static long[] bucket(List<Instant> times, LocalDate firstWeek) {
        long[] counts = new long[TREND_WEEKS];
        for (Instant time : times) {
            long week = ChronoUnit.WEEKS.between(firstWeek, time.atZone(ZoneOffset.UTC).toLocalDate());
            if (week >= 0 && week < TREND_WEEKS) {
                counts[(int) week]++;
            }
        }
        return counts;
    }
}
