package com.deanmanagement.testmanagement.project.internal.dto.issuetracker;

import jakarta.validation.constraints.Size;

/**
 * Either links an existing issue ({@code externalId}) or files a new one ({@code create: true}).
 * When creating, title and body are optional — the service fills both from the test result if they
 * are absent, which is the common path from a failed result.
 */
public record CreateIssueLinkRequest(
        @Size(max = 300) String externalId,
        /* Boxed, not primitive: Jackson rejects a record whose primitive component is absent from
         * the body, and "link an existing issue" bodies legitimately omit this. */
        Boolean create,
        @Size(max = 500) String title,
        @Size(max = 20000) String body
) {
    public boolean isCreate() {
        return Boolean.TRUE.equals(create);
    }
}
