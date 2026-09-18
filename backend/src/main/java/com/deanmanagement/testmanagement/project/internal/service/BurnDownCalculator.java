package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.effort.BurnDownResponse;
import com.deanmanagement.testmanagement.project.internal.dto.effort.BurnDownResponse.Point;
import com.deanmanagement.testmanagement.project.internal.dto.effort.BurnDownRow;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Computes a plan's burn-down from its results, on demand (PRD-036 §3.2): no snapshot tables and
 * no scheduled job. Remaining on a day is the estimates of the results that existed by the end of
 * that day and were not yet executed. Scope added later shows as a step up, which is the honest
 * picture. Estimates are live, so correcting one shifts past points too; that is accepted.
 */
final class BurnDownCalculator {

    /** A year of daily points; a longer plan shows its most recent year. */
    static final int MAX_POINTS = 366;

    private BurnDownCalculator() {
    }

    static BurnDownResponse calculate(List<BurnDownRow> allRows, LocalDate planCreated, LocalDate targetDate,
                                      LocalDate today) {
        List<BurnDownRow> rows = allRows.stream().filter(row -> !row.isLegacy()).toList();
        boolean hasLegacy = rows.size() < allRows.size();
        boolean hasEstimates = rows.stream().anyMatch(row -> row.estimateMinutes() != null);

        LocalDate scopeStartsAt = rows.stream().map(row -> dayOf(row.createdAt())).min(LocalDate::compareTo).orElse(null);
        LocalDate first = planCreated.isBefore(today.minusDays(MAX_POINTS - 1L)) ? today.minusDays(MAX_POINTS - 1L)
                : planCreated;

        // ponytail: days x rows. At 366 days that is fine into the tens of thousands of results;
        // sort the rows once and sweep if a plan ever gets bigger than that.
        List<Point> days = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            days.add(new Point(day, remainingAtEndOf(day, rows)));
        }
        return new BurnDownResponse(days, idealLine(rows, scopeStartsAt, targetDate, hasEstimates), scopeStartsAt,
                hasLegacy ? firstRecordedExecution(rows) : null, hasEstimates);
    }

    private static long remainingAtEndOf(LocalDate day, List<BurnDownRow> rows) {
        Instant endOfDay = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return rows.stream()
                .filter(row -> row.estimateMinutes() != null)
                .filter(row -> row.createdAt().isBefore(endOfDay))
                .filter(row -> row.executedAt() == null || !row.executedAt().isBefore(endOfDay))
                .mapToLong(BurnDownRow::estimateMinutes)
                .sum();
    }

    private static List<Point> idealLine(List<BurnDownRow> rows, LocalDate scopeStartsAt, LocalDate targetDate,
                                         boolean hasEstimates) {
        if (!hasEstimates || scopeStartsAt == null || targetDate == null || !targetDate.isAfter(scopeStartsAt)) {
            return List.of();
        }
        long start = remainingAtEndOf(scopeStartsAt, rows);
        long span = Math.min(ChronoUnit.DAYS.between(scopeStartsAt, targetDate), MAX_POINTS - 1L);
        LocalDate from = targetDate.minusDays(span);
        List<Point> line = new ArrayList<>();
        for (long i = 0; i <= span; i++) {
            line.add(new Point(from.plusDays(i), Math.round(start * (double) (span - i) / span)));
        }
        return line;
    }

    private static LocalDate firstRecordedExecution(List<BurnDownRow> rows) {
        return rows.stream().map(BurnDownRow::executedAt).filter(Objects::nonNull)
                .map(BurnDownCalculator::dayOf).min(LocalDate::compareTo).orElse(null);
    }

    private static LocalDate dayOf(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }
}
