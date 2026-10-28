package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * What a project pulls from its issue tracker as requirements (TM19), and how it maps them. One per
 * project; the connection and token are the project's {@link IssueTrackerConfig}. Only Azure DevOps
 * is supported, where the query is WIQL.
 */
@Entity
@Table(name = "requirement_sync_configs")
@Getter
@Setter
@NoArgsConstructor
public class RequirementSyncConfig extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private boolean enabled;

    /** Included in the scheduled sync, when the instance has one configured. */
    @Column(nullable = false)
    private boolean scheduled;

    /** The tracker's query language: WIQL for Azure DevOps. A flat query returning work item ids. */
    @Column(name = "tracker_query", nullable = false, columnDefinition = "TEXT")
    private String trackerQuery;

    /** Put in front of the work item id to form the external id, e.g. {@code AB#}; null for the bare id. */
    @Column(name = "external_id_prefix", length = 20)
    private String externalIdPrefix;

    /** Comma-separated tracker states that map to APPROVED, ignoring case. */
    @Column(name = "approved_states", nullable = false, length = 1000)
    private String approvedStates;

    /** Comma-separated tracker states that map to OBSOLETE; every other state is DRAFT. */
    @Column(name = "obsolete_states", nullable = false, length = 1000)
    private String obsoleteStates;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "last_error_at")
    private Instant lastErrorAt;
}
