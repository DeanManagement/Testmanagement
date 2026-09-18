package com.deanmanagement.testmanagement.project.internal.dto.testCase;

/**
 * What the caller may do in a case's review right now (PRD-033), so the UI doesn't repeat the
 * role and not-the-author rules. {@code reason} explains a refused approve, when there is one.
 */
public record ReviewCapabilitiesResponse(
        boolean reviewRequired,
        boolean canSubmit,
        boolean canApprove,
        String reason
) {
}
