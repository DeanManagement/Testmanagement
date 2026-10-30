package com.deanmanagement.testmanagement.project.internal.dto.bugstatus;

import com.deanmanagement.testmanagement.project.internal.dto.resultstatus.ResultStatusColor;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateBugStatusRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Pattern(regexp = ResultStatusColor.PATTERN) String color,
        @NotNull BugReportStatus category
) {
}
