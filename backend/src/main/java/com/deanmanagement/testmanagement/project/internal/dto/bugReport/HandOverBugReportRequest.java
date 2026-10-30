package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

/** TM50: blank files a new work item; otherwise the id of one to link instead. */
public record HandOverBugReportRequest(String existingExternalId) {
}
