package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

import com.deanmanagement.testmanagement.project.internal.entity.BugRelationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** TM46: this bug {@code type} the other one, named by key or UUID. */
public record RelateBugReportRequest(@NotBlank String relatedBug, @NotNull BugRelationType type) {
}
