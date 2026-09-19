package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** PRD-047: the result, and optionally the step of it, where an existing bug showed up again. */
public record LinkBugReportRequest(@NotNull UUID testResultId, UUID stepResultId) {
}
