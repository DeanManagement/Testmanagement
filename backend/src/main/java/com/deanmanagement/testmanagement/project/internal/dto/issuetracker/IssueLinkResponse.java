package com.deanmanagement.testmanagement.project.internal.dto.issuetracker;

import com.deanmanagement.testmanagement.project.internal.entity.IssueState;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;

import java.time.Instant;
import java.util.UUID;

/**
 * A linked issue. {@code stateCheckedAt} is exposed so the UI can show how fresh the OPEN/CLOSED
 * pill is rather than implying it is live.
 */
public record IssueLinkResponse(
        UUID id,
        UUID testResultId,
        IssueTrackerProviderType provider,
        String externalId,
        String url,
        String title,
        IssueState state,
        Instant stateCheckedAt
) {
}
