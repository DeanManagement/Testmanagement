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

import java.util.UUID;

/**
 * Grants one project access to one globally defined workflow (PRD-024). The assignment is the
 * entire authorization: a project member can only see and trigger workflows a row here exposes to
 * their project, and an unassigned workflow is reported as not found rather than forbidden.
 */
@Entity
@Table(name = "project_build_workflows",
        uniqueConstraints = @UniqueConstraint(name = "uq_project_build_workflows",
                columnNames = {"project_id", "build_workflow_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ProjectBuildWorkflow extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "build_workflow_id", nullable = false)
    private BuildWorkflow workflow;
}
