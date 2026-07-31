package com.deanmanagement.testmanagement.project.internal.issuetracker;

import com.deanmanagement.testmanagement.project.internal.entity.IssueState;

/**
 * A tracker issue as the tool sees it — the provider-scoped id used for later lookups, plus what is
 * needed to render a chip. Deliberately minimal: comments, labels and assignees stay in the tracker.
 */
public record Issue(
        String externalId,
        String url,
        String title,
        IssueState state
) {
}
