package com.deanmanagement.testmanagement.project.internal.dto.issuetracker;

import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
        Boolean active,
        // PRD-029 §3.1: the account email, required for Jira Cloud only and ignored otherwise.
        @Size(max = 255) String authUsername,
        /* PRD-026: Azure DevOps only, e.g. 7.1 or 6.0; blank means the default. Ignored for other providers. */
        @Size(max = 10) @Pattern(regexp = "^$|^\\d+\\.\\d+(-preview(\\.\\d+)?)?$",
                message = "must look like 7.1 or 6.0") String apiVersion,
        /* PRD-026: Azure DevOps only, the work item type bugs are filed as; blank means Bug. */
        @Size(max = 100) @Pattern(regexp = "^[\\p{L}\\p{N} ._-]*$",
                message = "may only contain letters, digits, spaces, dots, hyphens and underscores") String workItemType
) {
}
