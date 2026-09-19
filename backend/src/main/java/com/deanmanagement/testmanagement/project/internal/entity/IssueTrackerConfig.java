package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A project's connection to an issue tracker (PRD-010). At most one per project, and entirely
 * opt-in: with no config the tool makes no outbound calls at all, which keeps air-gapped installs
 * quiet.
 *
 * <p>The API token is stored AES-GCM encrypted (see {@code IssueTrackerTokenCipher}) and is never
 * returned by any endpoint.
 */
@Entity
@Table(name = "issue_tracker_configs")
@Getter
@Setter
@NoArgsConstructor
public class IssueTrackerConfig extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueTrackerProviderType provider;

    /** Instance root, e.g. {@code https://gitlab.com}. Self-hosted instances are SSRF-validated. */
    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    /** Provider-specific project identifier — for GitLab, {@code group/project} or a numeric id. */
    @Column(name = "project_ref", nullable = false, length = 300)
    private String projectRef;

    @Column(name = "api_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String apiTokenEncrypted;

    /**
     * The account the token belongs to, for trackers that authenticate with a username and token
     * pair — today only Jira Cloud, whose Basic auth is {@code email:apiToken} (PRD-029 §3.1). Not a
     * secret, and {@code null} for everything else.
     */
    @Column(name = "auth_username", length = 255)
    private String authUsername;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Last failure from the provider. Set on auth or transport errors so the UI can flag the config
     * as needing attention, and cleared on the next success.
     */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "last_error_at")
    private Instant lastErrorAt;

    /** Azure DevOps only (PRD-026): the {@code api-version} to send; null means the adapter's default. */
    @Column(name = "api_version", length = 10)
    private String apiVersion;

    /** Azure DevOps only: the work item type bugs are filed as; null means {@code Bug}. */
    @Column(name = "work_item_type", length = 100)
    private String workItemType;
}
