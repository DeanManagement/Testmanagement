package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A triggerable workflow an admin has defined on a registered build server (PRD-024): which
 * repository/job, which workflow file or branch, and what a trigger sends by default. Projects
 * gain access via {@link ProjectBuildWorkflow} assignments.
 */
@Entity
@Table(name = "build_workflows",
        uniqueConstraints = @UniqueConstraint(name = "uq_build_workflows_server_name",
                columnNames = {"build_server_config_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class BuildWorkflow extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "build_server_config_id", nullable = false)
    private BuildServerConfig buildServerConfig;

    /** Display name shown to testers, e.g. "Nightly regression suite". */
    @Column(nullable = false, length = 150)
    private String name;

    /**
     * Provider-specific repository/job identifier: GitLab project path or id, {@code owner/repo}
     * for GitHub/Forgejo, a numeric repo id for Woodpecker, a job path like
     * {@code folder/subfolder/job} for Jenkins.
     */
    @Column(name = "repo_ref", nullable = false, length = 300)
    private String repoRef;

    /**
     * The workflow within the repository where the provider needs one: a workflow file name for
     * GitHub/Forgejo Actions. Blank where {@link #repoRef} already identifies the pipeline
     * (GitLab, Woodpecker, Jenkins).
     */
    @Column(name = "workflow_ref", length = 300)
    private String workflowRef;

    /** Branch/ref triggered when the tester does not override it. */
    @Column(name = "default_ref", length = 200)
    private String defaultRef;

    /** JSON object of default trigger parameters/variables, editable per trigger. */
    @Column(name = "default_parameters", columnDefinition = "TEXT")
    private String defaultParameters;

    @Column(nullable = false)
    private boolean active = true;
}
