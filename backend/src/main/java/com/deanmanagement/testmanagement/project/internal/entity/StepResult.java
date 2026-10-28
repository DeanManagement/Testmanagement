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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "step_results")
@Getter
@Setter
@NoArgsConstructor
public class StepResult extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestResultStatus status;

    /** TM24: the project's name for the outcome; null means the category's default. */
    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custom_status_id")
    private ProjectResultStatus customStatus;

    @Column(columnDefinition = "TEXT")
    private String actualResult;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_result_id", nullable = false)
    private TestResult testResult;

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_step_id")
    private TestStep testStep;

    /*
     * The step's text when this result was created (TM25). Replacing a case's steps deletes the old
     * rows and the link above goes null, so without these an open run lost the instructions.
     */
    @Setter(AccessLevel.NONE)
    @Column(name = "step_action", columnDefinition = "TEXT")
    private String stepAction;

    @Setter(AccessLevel.NONE)
    @Column(name = "step_expected_result", columnDefinition = "TEXT")
    private String stepExpectedResult;

    @Setter(AccessLevel.NONE)
    @Column(name = "step_test_data", columnDefinition = "TEXT")
    private String stepTestData;

    /**
     * Order within the result (PRD-030): expanded steps come from several owners, each numbered from
     * 0. Null only for rows written before V62 whose step was already gone.
     */
    @Column(name = "position")
    private Integer position;

    /** Links the step and keeps a copy of its text, so the result still reads after the step is gone. */
    public void setTestStep(TestStep testStep) {
        this.testStep = testStep;
        this.stepAction = testStep == null ? null : testStep.getAction();
        this.stepExpectedResult = testStep == null ? null : testStep.getExpectedResult();
        this.stepTestData = testStep == null ? null : testStep.getTestData();
    }

    /** Recording another category drops a custom status of a different one (TM24), as on TestResult. */
    public void setStatus(TestResultStatus status) {
        if (customStatus != null && customStatus.getCategory() != status) {
            this.customStatus = null;
        }
        this.status = status;
    }

    /** TM24: the status a tester picked; the category follows from it. */
    public void setCustomStatus(ProjectResultStatus customStatus) {
        setStatus(customStatus.getCategory());
        this.customStatus = customStatus;
    }
}
