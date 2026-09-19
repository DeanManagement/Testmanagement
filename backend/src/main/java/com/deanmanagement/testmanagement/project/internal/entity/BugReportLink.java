package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A further place a bug showed up (PRD-047), besides the result it was found in. Typically a
 * regression: the same case failing again in a later run. The run comes from the result.
 */
@Entity
@Table(name = "bug_report_links")
@Getter
@Setter
@NoArgsConstructor
public class BugReportLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bug_report_id", nullable = false)
    private BugReport bugReport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_result_id", nullable = false)
    private TestResult testResult;

    /** The failing step, when the tester named one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_result_id")
    private StepResult stepResult;
}
