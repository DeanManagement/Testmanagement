package com.deanmanagement.testmanagement.project.internal.issuetracker;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;

/** A new issue to file. Body is provider-neutral Markdown, which GitLab, GitHub and Linear all take. */
public record IssueDraft(
        String title,
        String body,
        /* TM50: null leaves the tracker's default. */
        Priority priority
) {
    public IssueDraft(String title, String body) {
        this(title, body, null);
    }
}
