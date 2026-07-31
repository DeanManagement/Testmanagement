package com.deanmanagement.testmanagement.project.internal.issuetracker;

import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;

import java.util.List;

/**
 * One tracker's API, reduced to the three operations the tool needs (PRD-010 §3.2). Adapters
 * receive the config with the token already decrypted and must not persist or log it.
 *
 * <p>Implementations throw {@link com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException}
 * for anything the caller cannot fix — transport failure, auth rejection, rate limiting, malformed
 * responses — so the service layer has a single failure mode to handle.
 */
public interface IssueTrackerProvider {

    IssueTrackerProviderType type();

    /** Free-text search scoped to the configured project. Returns at most a page of matches. */
    List<Issue> search(DecryptedConfig config, String query);

    Issue create(DecryptedConfig config, IssueDraft draft);

    /** Fetches one issue by the provider-scoped id previously returned by search or create. */
    Issue get(DecryptedConfig config, String externalId);

    /** Verifies credentials and project reference without side effects, for the config form's test button. */
    void testConnection(DecryptedConfig config);

    /** A config paired with its plaintext token, assembled per call and never stored. */
    record DecryptedConfig(IssueTrackerConfig config, String token) {
        public String baseUrl() {
            return config.getBaseUrl();
        }

        public String projectRef() {
            return config.getProjectRef();
        }
    }
}
