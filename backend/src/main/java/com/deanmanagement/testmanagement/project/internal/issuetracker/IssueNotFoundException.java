package com.deanmanagement.testmanagement.project.internal.issuetracker;

import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;

/**
 * The tracker answered, but has no such issue: deleted, or out of the token's sight. About one
 * issue, unlike an {@link com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException},
 * which says the tracker itself is unusable and stops a poll for the whole project.
 */
public class IssueNotFoundException extends ResourceNotFoundException {

    public IssueNotFoundException(String message) {
        super(message);
    }
}
