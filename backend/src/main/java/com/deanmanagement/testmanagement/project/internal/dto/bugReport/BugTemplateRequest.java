package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

/** PRD-045: what a new bug report's empty fields start with. Blank clears a field's template. */
public record BugTemplateRequest(String description, String stepsToReproduce, String environment) {
}
