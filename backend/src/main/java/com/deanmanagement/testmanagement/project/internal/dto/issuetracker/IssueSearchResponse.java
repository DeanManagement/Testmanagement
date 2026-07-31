package com.deanmanagement.testmanagement.project.internal.dto.issuetracker;

import com.deanmanagement.testmanagement.project.internal.entity.IssueState;

/** One typeahead hit. Mirrors the provider's issue without exposing provider-specific fields. */
public record IssueSearchResponse(
        String externalId,
        String url,
        String title,
        IssueState state
) {
}
