package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.analytics.FlakyTestResponse;
import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse.Counts;
import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse.Criterion;
import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse.CriterionName;
import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse.Outcome;
import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse.Verdict;
import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResultRow;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.ReleaseGate;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.service.ReleaseReadinessService.Inputs;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The pure part of the release gate (PRD-037 §3.2): no database. */
class ReleaseReadinessEvaluationTest {

    private static final UUID CASE_A = UUID.randomUUID();
    private static final UUID CASE_B = UUID.randomUUID();
    private static final Instant MONDAY = Instant.parse("2026-09-14T10:00:00Z");
    private static final Instant WEDNESDAY = Instant.parse("2026-09-16T10:00:00Z");

    private static ReadinessResultRow row(UUID testCase, String parameterSet, TestResultStatus status, Instant runAt) {
        return new ReadinessResultRow(testCase, parameterSet, status, runAt, runAt);
    }

    private static Counts counts(int considered, int passed) {
        return new Counts(considered, passed, considered - passed, 0, 0, 0);
    }

    private static Inputs inputs(Counts counts, long blockerBugs, BigDecimal coverage, long flaky) {
        return new Inputs(counts, blockerBugs, coverage, flaky);
    }

    private static Criterion only(ReleaseGate gate, Inputs inputs) {
        List<Criterion> criteria = ReleaseReadinessService.evaluate(gate, inputs);
        assertThat(criteria).hasSize(1);
        return criteria.getFirst();
    }

    @Nested
    class LatestResult {

        @Test
        void aRetestThatPassedReplacesTheEarlierFailure() {
            Counts counts = ReleaseReadinessService.countLatest(List.of(
                    row(CASE_A, null, TestResultStatus.FAILED, MONDAY),
                    row(CASE_A, null, TestResultStatus.PASSED, WEDNESDAY)));

            assertThat(counts).isEqualTo(new Counts(1, 1, 0, 0, 0, 0));
        }

        @Test
        void orderIsByWhenTheRunHappenedNotTheOrderRowsArrive() {
            // A CI run backfilled later but executed on Monday must not override Wednesday's result.
            Counts counts = ReleaseReadinessService.countLatest(List.of(
                    row(CASE_A, null, TestResultStatus.PASSED, WEDNESDAY),
                    row(CASE_A, null, TestResultStatus.FAILED, MONDAY)));

            assertThat(counts.passed()).isEqualTo(1);
        }

        @Test
        void eachParameterSetIsItsOwnResult() {
            Counts counts = ReleaseReadinessService.countLatest(List.of(
                    row(CASE_A, "EUR", TestResultStatus.PASSED, MONDAY),
                    row(CASE_A, "CHF", TestResultStatus.PASSED, MONDAY),
                    row(CASE_A, "JPY", TestResultStatus.FAILED, MONDAY)));

            assertThat(counts).isEqualTo(new Counts(3, 2, 1, 0, 0, 0));
        }

        @Test
        void withinOneRunTheLaterRecordedResultWins() {
            Counts counts = ReleaseReadinessService.countLatest(List.of(
                    new ReadinessResultRow(CASE_A, null, TestResultStatus.FAILED, MONDAY, MONDAY),
                    new ReadinessResultRow(CASE_A, null, TestResultStatus.PASSED, MONDAY, MONDAY.plusSeconds(60))));

            assertThat(counts.passed()).isEqualTo(1);
        }

        @Test
        void pendingStaysPending() {
            Counts counts = ReleaseReadinessService.countLatest(List.of(
                    row(CASE_A, null, TestResultStatus.PASSED, MONDAY),
                    row(CASE_B, null, TestResultStatus.PENDING, WEDNESDAY)));

            assertThat(counts).isEqualTo(new Counts(2, 1, 0, 0, 0, 1));
        }
    }

    @Nested
    class PassRate {

        private final ReleaseGate gate = new ReleaseGate(new BigDecimal("98.00"), null, null, null);

        @Test
        void exactlyTheThresholdPasses() {
            Criterion criterion = only(gate, inputs(counts(50, 49), 0, null, 0));

            assertThat(criterion.outcome()).isEqualTo(Outcome.PASS);
            assertThat(criterion.actual()).isEqualByComparingTo("98.00");
        }

        @Test
        void justBelowTheThresholdFailsEvenWhenItWouldRoundUp() {
            // 97.995 % rounds to 98.00 for display but is below 98.
            Criterion criterion = only(gate, inputs(counts(200_000, 195_990), 0, null, 0));

            assertThat(criterion.outcome()).isEqualTo(Outcome.FAIL);
            assertThat(criterion.actual()).isEqualByComparingTo("98.00");
        }

        @Test
        void aPlanWithNothingExecutedFails() {
            Criterion criterion = only(gate, inputs(counts(0, 0), 0, null, 0));

            assertThat(criterion.outcome()).isEqualTo(Outcome.FAIL);
            assertThat(criterion.actual()).isEqualByComparingTo("0");
        }
    }

    @Nested
    class OtherCriteria {

        @Test
        void blockerBugsAtTheLimitPassAndOneMoreFails() {
            ReleaseGate gate = new ReleaseGate(null, 1, null, null);

            assertThat(only(gate, inputs(counts(0, 0), 1, null, 0)).outcome()).isEqualTo(Outcome.PASS);
            assertThat(only(gate, inputs(counts(0, 0), 2, null, 0)).outcome()).isEqualTo(Outcome.FAIL);
        }

        @Test
        void coverageWithoutRequirementsDoesNotApply() {
            Criterion criterion = only(new ReleaseGate(null, null, new BigDecimal("80"), null),
                    inputs(counts(0, 0), 0, null, 0));

            assertThat(criterion.outcome()).isEqualTo(Outcome.NOT_APPLICABLE);
            assertThat(criterion.actual()).isNull();
        }

        @Test
        void coverageComparesAgainstTheThreshold() {
            ReleaseGate gate = new ReleaseGate(null, null, new BigDecimal("80"), null);

            assertThat(only(gate, inputs(counts(0, 0), 0, new BigDecimal("80.0"), 0)).outcome()).isEqualTo(Outcome.PASS);
            assertThat(only(gate, inputs(counts(0, 0), 0, new BigDecimal("79.99"), 0)).outcome()).isEqualTo(Outcome.FAIL);
        }

        @Test
        void flakyTestsAtTheLimitPass() {
            Criterion criterion = only(new ReleaseGate(null, null, null, 2), inputs(counts(0, 0), 0, null, 2));

            assertThat(criterion.name()).isEqualTo(CriterionName.FLAKY_TESTS);
            assertThat(criterion.outcome()).isEqualTo(Outcome.PASS);
        }

        @Test
        void onlyFlakyCasesThePlanExecutedCount() {
            UUID notInPlan = UUID.randomUUID();
            List<FlakyTestResponse> analysis = List.of(
                    new FlakyTestResponse(CASE_A, "P-1", "a", 0.8, 0.5, 10, true),
                    new FlakyTestResponse(CASE_B, "P-2", "b", 0.1, 0.1, 10, false),
                    new FlakyTestResponse(notInPlan, "P-3", "c", 0.9, 0.5, 10, true));

            long flaky = ReleaseReadinessService.flakyCasesIn(List.of(
                    row(CASE_A, "EUR", TestResultStatus.PASSED, MONDAY),
                    row(CASE_A, "CHF", TestResultStatus.PASSED, MONDAY),
                    row(CASE_B, null, TestResultStatus.PASSED, MONDAY)), analysis);

            assertThat(flaky).isEqualTo(1);
        }
    }

    @Nested
    class Verdicts {

        @Test
        void noThresholdsMeansNoCriteria() {
            List<Criterion> criteria = ReleaseReadinessService.evaluate(ReleaseGate.NONE, inputs(counts(10, 10), 0, null, 0));

            assertThat(criteria).isEmpty();
            assertThat(ReleaseReadinessService.verdictOf(criteria)).isEqualTo(Verdict.NO_CRITERIA);
        }

        @Test
        void oneFailingCriterionAmongPassingOnesIsNoGo() {
            ReleaseGate gate = new ReleaseGate(new BigDecimal("90"), 0, new BigDecimal("50"), 5);

            List<Criterion> criteria = ReleaseReadinessService.evaluate(gate, inputs(counts(10, 10), 1, new BigDecimal("60"), 0));

            assertThat(criteria).extracting(Criterion::outcome)
                    .containsExactly(Outcome.PASS, Outcome.FAIL, Outcome.PASS, Outcome.PASS);
            assertThat(ReleaseReadinessService.verdictOf(criteria)).isEqualTo(Verdict.NO_GO);
        }

        @Test
        void notApplicableDoesNotBlockAGo() {
            ReleaseGate gate = new ReleaseGate(new BigDecimal("90"), null, new BigDecimal("80"), null);

            List<Criterion> criteria = ReleaseReadinessService.evaluate(gate, inputs(counts(10, 10), 0, null, 0));

            assertThat(ReleaseReadinessService.verdictOf(criteria)).isEqualTo(Verdict.GO);
        }
    }
}
