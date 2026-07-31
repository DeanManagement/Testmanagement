package com.deanmanagement.testmanagement.project.internal.dto.testrun;

import java.util.UUID;

/** (test run id, allure report id) pair for batch-resolving report links on list pages. */
public record RunAllureReportId(UUID runId, UUID reportId) {
}
