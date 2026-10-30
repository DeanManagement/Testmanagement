package com.deanmanagement.testmanagement.project.internal.dto.bugstatus;

import com.deanmanagement.testmanagement.project.internal.dto.resultstatus.ResultStatusColor;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateBugStatusRequest(
        @Size(max = 100) String name,
        @Pattern(regexp = ResultStatusColor.PATTERN) String color,
        Integer position,
        Boolean archived
) {
}
