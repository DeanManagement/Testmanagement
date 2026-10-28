package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A file attached to a test case (PRD-044), a bug report (PRD-051), a test step or a step result
 * (TM05: a step's documents, a result's evidence); exactly one owner is set, which
 * {@code ck_attachments_one_owner} enforces. Immutable once stored: replacing a file is a delete and
 * an upload. Lists read {@code AttachmentSummary} projections, so the bytes are only loaded when a
 * single attachment is downloaded.
 */
@Entity
@Table(name = "attachments")
@Getter
@Setter
@NoArgsConstructor
public class Attachment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_case_id")
    private TestCase testCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bug_report_id")
    private BugReport bugReport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_step_id")
    private TestStep testStep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_result_id")
    private StepResult stepResult;

    @Column(nullable = false)
    private String fileName;

    /** The type the content check verified, not the one the client declared. */
    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false, columnDefinition = "BYTEA")
    private byte[] data;
}
