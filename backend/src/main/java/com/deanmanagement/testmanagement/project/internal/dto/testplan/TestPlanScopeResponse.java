package com.deanmanagement.testmanagement.project.internal.dto.testplan;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * A plan's scope (PRD-053 §8): the cases it intends to execute, each with its latest executed result
 * across the plan's non-aborted runs. A case whose results are all PENDING, or that no run holds,
 * has no latest status: it is not executed yet.
 */
public record TestPlanScopeResponse(Summary summary, List<Item> cases) {

    public record Summary(int total, int executed, int notExecuted, int passed, int failed, int blocked,
                          int skipped) {
    }

    public record Item(UUID testCaseId, String key, String title, Priority priority,
                       @Nullable TestResultStatus latestStatus) {
    }
}
