package com.deanmanagement.testmanagement.project.internal.dto.effort;

import java.time.LocalDate;
import java.util.List;

/**
 * A plan's burn-down (PRD-036 §3.2). Days are UTC calendar days.
 *
 * @param days                 remaining estimated minutes at the end of each day, up to today
 * @param idealLine            a straight line from the scope on {@code scopeStartsAt} to zero on
 *                             the target date; empty without a target date or any estimated scope
 * @param scopeStartsAt        the first day the plan had results; null while it has none
 * @param historyAvailableFrom set when results executed before execution times were recorded had
 *                             to be left out: the first day with a recorded execution, or null if
 *                             there is none yet
 * @param hasEstimates         false when no result's case is estimated, so the chart would be a
 *                             meaningless flat zero
 */
public record BurnDownResponse(
        List<Point> days,
        List<Point> idealLine,
        LocalDate scopeStartsAt,
        LocalDate historyAvailableFrom,
        boolean hasEstimates
) {
    public record Point(LocalDate date, long remainingMinutes) {
    }
}
