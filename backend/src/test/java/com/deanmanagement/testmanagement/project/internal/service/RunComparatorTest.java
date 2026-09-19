package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.comparison.ComparableResult;
import com.deanmanagement.testmanagement.project.internal.dto.comparison.RunComparisonResponse.Category;
import com.deanmanagement.testmanagement.project.internal.dto.comparison.RunComparisonResponse.Counts;
import com.deanmanagement.testmanagement.project.internal.dto.comparison.RunComparisonResponse.Row;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.UUID;

import static com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus.BLOCKED;
import static com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus.FAILED;
import static com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus.PASSED;
import static com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus.PENDING;
import static com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus.SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** The PRD-038 §3.2 classification, without a database. */
class RunComparatorTest {

    private static final UUID CASE_A = UUID.randomUUID();
    private static final UUID CASE_B = UUID.randomUUID();

    private static ComparableResult result(UUID testCase, String key, String set, TestResultStatus status, Integer version) {
        return new ComparableResult(UUID.randomUUID(), testCase, key, "Title of " + key, set, status, version);
    }

    private static ComparableResult result(UUID testCase, TestResultStatus status) {
        return result(testCase, "P-1", null, status, 1);
    }

    private static Row onlyRow(List<ComparableResult> base, List<ComparableResult> head) {
        List<Row> rows = RunComparator.compare(base, head).rows();
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    @Nested
    class Categories {

        @ParameterizedTest(name = "{0} -> {1} is {2}")
        @CsvSource({
                "PASSED,  FAILED,  NEWLY_FAILING",
                "PASSED,  BLOCKED, NEWLY_FAILING",
                "FAILED,  PASSED,  FIXED",
                "BLOCKED, PASSED,  FIXED",
                "FAILED,  FAILED,  STILL_FAILING",
                "BLOCKED, BLOCKED, STILL_FAILING",
                "PASSED,  PASSED,  UNCHANGED",
                "SKIPPED, SKIPPED, UNCHANGED",
                "BLOCKED, FAILED,  OTHER_CHANGE",
                "FAILED,  BLOCKED, OTHER_CHANGE",
                "PASSED,  SKIPPED, OTHER_CHANGE",
                "SKIPPED, FAILED,  OTHER_CHANGE",
                "PENDING, PASSED,  OTHER_CHANGE",
                "FAILED,  PENDING, OTHER_CHANGE",
        })
        void followTheTable(TestResultStatus base, TestResultStatus head, Category expected) {
            Row row = onlyRow(List.of(result(CASE_A, base)), List.of(result(CASE_A, head)));

            assertThat(row.category()).isEqualTo(expected);
            assertThat(row.baseStatus()).isEqualTo(base);
            assertThat(row.headStatus()).isEqualTo(head);
        }

        @Test
        void aCaseOnlyInTheHeadIsAdded() {
            Row row = onlyRow(List.of(), List.of(result(CASE_A, FAILED)));

            assertThat(row.category()).isEqualTo(Category.ADDED);
            assertThat(row.baseStatus()).isNull();
            assertThat(row.baseResultId()).isNull();
        }

        @Test
        void aCaseOnlyInTheBaseIsRemoved() {
            Row row = onlyRow(List.of(result(CASE_A, PASSED)), List.of());

            assertThat(row.category()).isEqualTo(Category.REMOVED);
            assertThat(row.headStatus()).isNull();
        }
    }

    @Nested
    class Matching {

        @Test
        void parameterSetsOfOneCaseAreComparedIndependently() {
            List<Row> rows = RunComparator.compare(
                    List.of(result(CASE_A, "P-1", "EUR", PASSED, 1), result(CASE_A, "P-1", "JPY", FAILED, 1)),
                    List.of(result(CASE_A, "P-1", "EUR", FAILED, 1), result(CASE_A, "P-1", "JPY", PASSED, 1))).rows();

            assertThat(rows).extracting(Row::parameterSetName, Row::category).containsExactly(
                    tuple("EUR", Category.NEWLY_FAILING),
                    tuple("JPY", Category.FIXED));
        }

        @Test
        void aRenamedParameterSetIsOneRemovedAndOneAdded() {
            List<Row> rows = RunComparator.compare(
                    List.of(result(CASE_A, "P-1", "Euro", PASSED, 1)),
                    List.of(result(CASE_A, "P-1", "EUR", PASSED, 1))).rows();

            assertThat(rows).extracting(Row::category).containsExactly(Category.ADDED, Category.REMOVED);
        }

        @Test
        void anOrdinaryCaseIsNotTheSameAsOneOfItsParameterSets() {
            List<Row> rows = RunComparator.compare(
                    List.of(result(CASE_A, "P-1", null, PASSED, 1)),
                    List.of(result(CASE_A, "P-1", "EUR", PASSED, 1))).rows();

            assertThat(rows).extracting(Row::category).containsExactly(Category.ADDED, Category.REMOVED);
        }

        @Test
        void duplicatesInARunCollapseToTheWorstAndAreCounted() {
            ComparableResult failed = result(CASE_A, FAILED);

            Row row = onlyRow(List.of(result(CASE_A, PASSED)),
                    List.of(result(CASE_A, PASSED), failed, result(CASE_A, SKIPPED)));

            assertThat(row.category()).isEqualTo(Category.NEWLY_FAILING);
            assertThat(row.headResultId()).isEqualTo(failed.resultId());
            assertThat(row.duplicates()).isEqualTo(2);
        }

        @Test
        void aBlockedDuplicateLosesToAFailedOne() {
            Row row = onlyRow(List.of(result(CASE_A, BLOCKED), result(CASE_A, FAILED)), List.of(result(CASE_A, FAILED)));

            assertThat(row.baseStatus()).isEqualTo(FAILED);
            assertThat(row.category()).isEqualTo(Category.STILL_FAILING);
        }
    }

    @Nested
    class VersionChange {

        @Test
        void isFlaggedWhenBothVersionsAreKnownAndDiffer() {
            Row row = onlyRow(List.of(result(CASE_A, "P-1", null, PASSED, 2)), List.of(result(CASE_A, "P-1", null, FAILED, 3)));

            assertThat(row.versionChanged()).isTrue();
        }

        @Test
        void isNotFlaggedWhenEitherVersionIsUnknown() {
            Row row = onlyRow(List.of(result(CASE_A, "P-1", null, PASSED, null)), List.of(result(CASE_A, "P-1", null, FAILED, 3)));

            assertThat(row.versionChanged()).isFalse();
        }
    }

    @Nested
    class OrderAndCounts {

        @Test
        void rowsAreByCategoryThenCaseKeyInNumberOrder() {
            UUID case10 = UUID.randomUUID();
            UUID case9 = UUID.randomUUID();
            List<Row> rows = RunComparator.compare(
                    List.of(result(case10, "P-10", null, PASSED, 1), result(case9, "P-9", null, PASSED, 1),
                            result(CASE_B, "P-2", null, FAILED, 1)),
                    List.of(result(case10, "P-10", null, FAILED, 1), result(case9, "P-9", null, FAILED, 1),
                            result(CASE_B, "P-2", null, PASSED, 1))).rows();

            assertThat(rows).extracting(Row::testCaseKey).containsExactly("P-9", "P-10", "P-2");
        }

        @Test
        void everyCategoryIsCounted() {
            Counts counts = RunComparator.compare(
                    List.of(result(CASE_A, "P-1", null, PASSED, 1), result(CASE_B, "P-2", null, PASSED, 1)),
                    List.of(result(CASE_A, "P-1", null, PASSED, 1), result(UUID.randomUUID(), "P-3", null, FAILED, 1)))
                    .counts();

            assertThat(counts).isEqualTo(new Counts(0, 0, 0, 1, 1, 0, 1));
        }

        @Test
        void keysThatAreNotNumberedSortAsText() {
            assertThat(RunComparator.compareKeys("P-9", "P-10")).isNegative();
            assertThat(RunComparator.compareKeys("B-1", "A-2")).isPositive();
            assertThat(RunComparator.compareKeys("login", "P-1")).isPositive();
        }
    }
}
