package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.comparison.ComparableResult;
import com.deanmanagement.testmanagement.project.internal.dto.comparison.RunComparisonResponse.Category;
import com.deanmanagement.testmanagement.project.internal.dto.comparison.RunComparisonResponse.Counts;
import com.deanmanagement.testmanagement.project.internal.dto.comparison.RunComparisonResponse.Row;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Classifies how each (test case, parameter set) moved between two runs (PRD-038 §3.2). Pure.
 *
 * <p>Matching is by case id, never title: CI ingestion already resolves a reported test to one
 * case, so the same JUnit test in two uploads is the same id. A renamed parameter set is therefore
 * one removed and one added row, which is right, since each result records the set it ran with.
 */
final class RunComparator {

    /** Worst first; a run holding the same key twice is shown by its worst result. */
    private static final List<TestResultStatus> SEVERITY = List.of(TestResultStatus.FAILED, TestResultStatus.BLOCKED,
            TestResultStatus.SKIPPED, TestResultStatus.PENDING, TestResultStatus.PASSED);

    /** A long holds any 18-digit number, and no generated key comes close. */
    private static final int MAX_KEY_DIGITS = 18;

    private RunComparator() {
    }

    record Comparison(Counts counts, List<Row> rows) {
    }

    private record Key(UUID testCaseId, String parameterSetName) {
    }

    /** One run's results for a key, collapsed: the worst one stands for them all. */
    private record Collapsed(ComparableResult shown, int extra) {
    }

    static Comparison compare(Collection<ComparableResult> base, Collection<ComparableResult> head) {
        Map<Key, Collapsed> before = collapse(base);
        Map<Key, Collapsed> after = collapse(head);
        Set<Key> keys = new LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());

        List<Row> rows = new ArrayList<>();
        for (Key key : keys) {
            rows.add(row(before.get(key), after.get(key)));
        }
        rows.sort(Comparator.comparing(Row::category)
                .thenComparing(Row::testCaseKey, RunComparator::compareKeys)
                .thenComparing(Row::parameterSetName, Comparator.nullsFirst(Comparator.naturalOrder())));
        return new Comparison(count(rows), rows);
    }

    private static Map<Key, Collapsed> collapse(Collection<ComparableResult> results) {
        Map<Key, Collapsed> collapsed = new LinkedHashMap<>();
        for (ComparableResult result : results) {
            collapsed.merge(new Key(result.testCaseId(), result.parameterSetName()), new Collapsed(result, 0),
                    (a, b) -> new Collapsed(worse(a.shown(), b.shown()), a.extra() + b.extra() + 1));
        }
        return collapsed;
    }

    private static ComparableResult worse(ComparableResult a, ComparableResult b) {
        return SEVERITY.indexOf(b.status()) < SEVERITY.indexOf(a.status()) ? b : a;
    }

    private static Row row(Collapsed before, Collapsed after) {
        ComparableResult base = before == null ? null : before.shown();
        ComparableResult head = after == null ? null : after.shown();
        ComparableResult any = head != null ? head : base;
        boolean versionChanged = base != null && head != null && base.executedVersion() != null
                && head.executedVersion() != null && !base.executedVersion().equals(head.executedVersion());
        int duplicates = (before == null ? 0 : before.extra()) + (after == null ? 0 : after.extra());
        return new Row(categoryOf(base == null ? null : base.status(), head == null ? null : head.status()),
                any.testCaseId(), any.testCaseKey(), any.title(), any.parameterSetName(),
                base == null ? null : base.status(), head == null ? null : head.status(),
                base == null ? null : base.resultId(), head == null ? null : head.resultId(),
                versionChanged, duplicates);
    }

    /**
     * The PRD-038 table. Only FAILED to FAILED or BLOCKED to BLOCKED is "still failing": BLOCKED to
     * FAILED means the environment problem went away and a real failure appeared, which a reader
     * should see as a change.
     */
    static Category categoryOf(TestResultStatus base, TestResultStatus head) {
        if (base == null) {
            return Category.ADDED;
        }
        if (head == null) {
            return Category.REMOVED;
        }
        if (base == head) {
            return isFailing(base) ? Category.STILL_FAILING : Category.UNCHANGED;
        }
        if (base == TestResultStatus.PASSED && isFailing(head)) {
            return Category.NEWLY_FAILING;
        }
        if (isFailing(base) && head == TestResultStatus.PASSED) {
            return Category.FIXED;
        }
        return Category.OTHER_CHANGE;
    }

    private static boolean isFailing(TestResultStatus status) {
        return status == TestResultStatus.FAILED || status == TestResultStatus.BLOCKED;
    }

    private static Counts count(List<Row> rows) {
        Map<Category, Integer> counts = new EnumMap<>(Category.class);
        rows.forEach(row -> counts.merge(row.category(), 1, Integer::sum));
        return new Counts(counts.getOrDefault(Category.NEWLY_FAILING, 0), counts.getOrDefault(Category.FIXED, 0),
                counts.getOrDefault(Category.STILL_FAILING, 0), counts.getOrDefault(Category.ADDED, 0),
                counts.getOrDefault(Category.REMOVED, 0), counts.getOrDefault(Category.OTHER_CHANGE, 0),
                counts.getOrDefault(Category.UNCHANGED, 0));
    }

    /** Case keys in number order, so PROJ-9 comes before PROJ-10; anything else in text order. */
    static int compareKeys(String a, String b) {
        String numberA = keyNumber(a);
        String numberB = keyNumber(b);
        if (numberA != null && numberB != null
                && a.substring(0, a.length() - numberA.length()).equals(b.substring(0, b.length() - numberB.length()))) {
            return Long.compare(Long.parseLong(numberA), Long.parseLong(numberB));
        }
        return a.compareTo(b);
    }

    /** The digits after the last dash, short enough to be a long; null if there are none. */
    private static String keyNumber(String key) {
        String digits = key.substring(key.lastIndexOf('-') + 1);
        boolean numbered = !digits.isEmpty() && digits.length() <= MAX_KEY_DIGITS
                && digits.chars().allMatch(Character::isDigit);
        return numbered ? digits : null;
    }
}
