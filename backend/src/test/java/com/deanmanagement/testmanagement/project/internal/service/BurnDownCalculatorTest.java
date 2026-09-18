package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.effort.BurnDownResponse;
import com.deanmanagement.testmanagement.project.internal.dto.effort.BurnDownResponse.Point;
import com.deanmanagement.testmanagement.project.internal.dto.effort.BurnDownRow;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Burn-down arithmetic on fixed days (PRD-036 §3.2); September 2026, all times UTC. */
class BurnDownCalculatorTest {

    private static final LocalDate PLAN_CREATED = day(1);
    private static final LocalDate TARGET = day(5);

    private static LocalDate day(int dayOfMonth) {
        return LocalDate.of(2026, 9, dayOfMonth);
    }

    private static Instant noonOn(int dayOfMonth) {
        return Instant.parse("2026-09-%02dT12:00:00Z".formatted(dayOfMonth));
    }

    private static BurnDownRow pending(int createdDay, Integer estimate) {
        return new BurnDownRow(noonOn(createdDay), null, TestResultStatus.PENDING, estimate);
    }

    private static BurnDownRow executed(int createdDay, int executedDay, Integer estimate) {
        return new BurnDownRow(noonOn(createdDay), noonOn(executedDay), TestResultStatus.PASSED, estimate);
    }

    private static List<Long> remaining(BurnDownResponse response) {
        return response.days().stream().map(Point::remainingMinutes).toList();
    }

    @Test
    void remainingDropsOnTheDayAResultIsExecuted() {
        BurnDownResponse response = BurnDownCalculator.calculate(
                List.of(executed(1, 2, 30), executed(1, 3, 20), pending(1, 10)), PLAN_CREATED, TARGET, day(4));

        assertThat(response.days()).extracting(Point::date).containsExactly(day(1), day(2), day(3), day(4));
        assertThat(remaining(response)).containsExactly(60L, 30L, 10L, 10L);
    }

    @Test
    void scopeAddedLaterShowsAsAStepUp() {
        BurnDownResponse response = BurnDownCalculator.calculate(
                List.of(executed(1, 2, 30), pending(3, 45)), PLAN_CREATED, TARGET, day(3));

        assertThat(remaining(response)).containsExactly(30L, 0L, 45L);
    }

    @Test
    void theActualLineStopsTodayAndTheIdealLineRunsToTheTargetDate() {
        BurnDownResponse response = BurnDownCalculator.calculate(List.of(pending(1, 40)), PLAN_CREATED, TARGET, day(2));

        assertThat(response.days()).hasSize(2);
        assertThat(response.idealLine()).containsExactly(new Point(day(1), 40), new Point(day(2), 30),
                new Point(day(3), 20), new Point(day(4), 10), new Point(day(5), 0));
    }

    @Test
    void theIdealLineStartsWhereTheScopeStarts() {
        BurnDownResponse response = BurnDownCalculator.calculate(List.of(pending(3, 40)), PLAN_CREATED, TARGET, day(4));

        assertThat(response.scopeStartsAt()).isEqualTo(day(3));
        assertThat(response.idealLine()).containsExactly(new Point(day(3), 40), new Point(day(4), 20),
                new Point(day(5), 0));
    }

    @Test
    void withoutATargetDateThereIsNoIdealLine() {
        BurnDownResponse response = BurnDownCalculator.calculate(List.of(pending(1, 40)), PLAN_CREATED, null, day(2));

        assertThat(response.idealLine()).isEmpty();
    }

    @Test
    void resultsExecutedBeforeExecutionTimesWereRecordedAreLeftOut() {
        BurnDownRow legacy = new BurnDownRow(noonOn(1), null, TestResultStatus.PASSED, 500);

        BurnDownResponse response = BurnDownCalculator.calculate(
                List.of(legacy, executed(2, 3, 30)), PLAN_CREATED, TARGET, day(3));

        assertThat(remaining(response)).containsExactly(0L, 30L, 0L);
        assertThat(response.historyAvailableFrom()).isEqualTo(day(3));
    }

    @Test
    void historyIsCompleteWhenNothingHadToBeLeftOut() {
        BurnDownResponse response = BurnDownCalculator.calculate(List.of(executed(1, 2, 30)), PLAN_CREATED, TARGET, day(2));

        assertThat(response.historyAvailableFrom()).isNull();
    }

    @Test
    void aPlanWithoutAnyEstimateSaysSoInsteadOfDrawingAFlatZero() {
        BurnDownResponse response = BurnDownCalculator.calculate(List.of(pending(1, null)), PLAN_CREATED, TARGET, day(2));

        assertThat(response.hasEstimates()).isFalse();
        assertThat(response.idealLine()).isEmpty();
    }

    @Test
    void anEmptyPlanHasNoScope() {
        BurnDownResponse response = BurnDownCalculator.calculate(List.of(), PLAN_CREATED, TARGET, day(2));

        assertThat(response.scopeStartsAt()).isNull();
        assertThat(remaining(response)).containsExactly(0L, 0L);
    }

    @Test
    void aLongRunningPlanShowsItsMostRecentYear() {
        LocalDate today = LocalDate.of(2028, 9, 1);

        BurnDownResponse response = BurnDownCalculator.calculate(List.of(pending(1, 40)), PLAN_CREATED, null, today);

        assertThat(response.days()).hasSize(BurnDownCalculator.MAX_POINTS);
        assertThat(response.days().getLast().date()).isEqualTo(today);
        assertThat(response.days().getFirst().remainingMinutes()).isEqualTo(40);
    }
}
