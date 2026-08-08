package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One triggered execution of a {@link BuildWorkflow} from one project (PRD-024). The workflow
 * name is denormalised and the workflow FK nullable so run history survives a workflow or server
 * being deleted; {@code testRun} is set when the pipeline reports results back through the
 * external API with this run's id.
 */
@Entity
@Table(name = "pipeline_runs")
@Getter
@Setter
@NoArgsConstructor
public class PipelineRun extends BaseEntity {

    /** Null once the workflow (or its server) has been deleted; the run keeps its history. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "build_workflow_id")
    private BuildWorkflow workflow;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "workflow_name", nullable = false, length = 150)
    private String workflowName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PipelineRunStatus status = PipelineRunStatus.TRIGGERED;

    /**
     * Provider-scoped run identifier: GitLab pipeline id, Actions run id, Woodpecker number,
     * Jenkins build number (or {@code queue:<id>} while still queued). Null while a dispatch-style
     * provider has not been correlated yet.
     */
    @Column(name = "external_run_id", length = 200)
    private String externalRunId;

    @Column(name = "external_url", length = 1000)
    private String externalUrl;

    @Column(name = "triggered_ref", length = 200)
    private String triggeredRef;

    /** JSON object of the parameters actually sent, for display and reproducibility. */
    @Column(columnDefinition = "TEXT")
    private String parameters;

    /** The test run created when results were reported back, if any. First report wins. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_run_id")
    private TestRun testRun;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "last_polled_at")
    private Instant lastPolledAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
