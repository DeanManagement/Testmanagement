package com.deanmanagement.testmanagement.project.internal.issuetracker;

/** A new issue to file. Body is provider-neutral Markdown, which GitLab, GitHub and Linear all take. */
public record IssueDraft(
        String title,
        String body
) {
}
