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
 * An issue in an external tracker linked to a single test result (PRD-010).
 *
 * <p>Provider and URL are denormalised onto the link rather than read through the project's config,
 * so links stay meaningful — and still open in a browser — after the config is deleted or switched
 * to a different provider.
 */
@Entity
@Table(name = "issue_links")
@Getter
@Setter
@NoArgsConstructor
public class IssueLink extends BaseEntity {

    @Column(name = "test_result_id", nullable = false)
    private UUID testResultId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueTrackerProviderType provider;

    /** Provider-scoped identifier, e.g. {@code group/project#42} for GitLab. */
    @Column(name = "external_id", nullable = false, length = 300)
    private String externalId;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueState state = IssueState.UNKNOWN;

    @Column(name = "state_checked_at")
    private Instant stateCheckedAt;
}
