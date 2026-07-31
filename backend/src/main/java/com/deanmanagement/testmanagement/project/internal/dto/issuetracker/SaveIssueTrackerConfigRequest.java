package com.deanmanagement.testmanagement.project.internal.dto.issuetracker;

import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create or update a project's tracker config. {@code apiToken} is write-only and optional on
 * update — omitting it keeps the stored token, so an admin can change the project reference without
 * having to paste the secret again.
 */
public record SaveIssueTrackerConfigRequest(
        @NotNull IssueTrackerProviderType provider,
        @NotBlank @Size(max = 500) String baseUrl,
        @NotBlank @Size(max = 300) String projectRef,
        @Size(max = 500) String apiToken,
        Boolean active
) {
}
