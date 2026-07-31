package com.deanmanagement.testmanagement.project.internal.dto.issuetracker;

import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;

/**
 * Whether this project can link issues, readable by any member.
 *
 * <p>Exists so the execution screen can decide whether to offer "link an issue" without exposing
 * the admin-only config — a tester needs to know a tracker is available, not where it lives or
 * which account it uses.
 */
public record IssueTrackerStatusResponse(
        boolean configured,
        IssueTrackerProviderType provider
) {
}
