package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.StepResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestResultResponse;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * What a report prints to explain a result.
 *
 * <p>A result executed as a whole carries its observation in {@code comment}. One executed step by
 * step — in the run view or through {@code record_step_result} — carries it on the steps instead,
 * and its comment stays empty. Reports printed only the comment, so every step-recorded failure
 * appeared as FAILED with nothing beside it: the run page showed why, the report that gets passed
 * around did not.
 */
final class ResultEvidence {

    static final String NONE = "-";
    private static final Set<TestResultStatus> WORTH_EXPLAINING =
            Set.of(TestResultStatus.FAILED, TestResultStatus.BLOCKED, TestResultStatus.SKIPPED);

    private ResultEvidence() {
    }

    /** The result's comment, else what was observed at each step that did not pass, else {@link #NONE}. */
    static String of(TestResultResponse result) {
        if (result.comment() != null && !result.comment().isBlank()) {
            return result.comment();
        }
        List<StepResultResponse> ordered = result.stepResults() == null ? List.of()
                : result.stepResults().stream()
                        .sorted(Comparator.comparingInt(StepResultResponse::orderIndex))
                        .toList();
        // Numbered by position, as the run view and record_step_result number them, not by
        // orderIndex, whose base is an implementation detail.
        String fromSteps = IntStream.range(0, ordered.size())
                .filter(i -> WORTH_EXPLAINING.contains(ordered.get(i).status()))
                .filter(i -> ordered.get(i).actualResult() != null
                        && !ordered.get(i).actualResult().isBlank())
                .mapToObj(i -> "Step " + (i + 1) + ": " + ordered.get(i).actualResult())
                .collect(Collectors.joining("\n"));
        return fromSteps.isEmpty() ? NONE : fromSteps;
    }
}
