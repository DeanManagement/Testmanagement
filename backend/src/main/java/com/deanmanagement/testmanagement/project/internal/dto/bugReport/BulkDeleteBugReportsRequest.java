package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BulkDeleteBugReportsRequest(
        @NotEmpty @Size(max = BulkUpdateBugReportsRequest.MAX_IDS) List<UUID> ids
) {
}
