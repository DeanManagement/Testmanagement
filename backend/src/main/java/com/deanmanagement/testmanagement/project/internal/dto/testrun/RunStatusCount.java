package com.deanmanagement.testmanagement.project.internal.dto.testrun;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;

import java.util.UUID;

/** One row of the per-run result-status aggregate used by test-run list projections. */
public record RunStatusCount(UUID runId, TestResultStatus status, long count) {
}
