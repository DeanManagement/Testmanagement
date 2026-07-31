package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "test_results")
@Getter
@Setter
@NoArgsConstructor
public class TestResult extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestResultStatus status;

    @Column(columnDefinition = "TEXT")
    private String comment;

    private String defectLink;

    /**
     * Which version of the test case this result executed (PRD-011). Null for results recorded
     * before versioning existed — the wording they ran against was never captured, and claiming
     * they ran v1 would be a false audit record.
     */
    @Column(name = "executed_version")
    private Integer executedVersion;

    /**
     * Which parameter set this result executed, when the case is parameterized (PRD-015). Null for
     * an ordinary case, which is the overwhelmingly common shape.
     */
    @Column(name = "parameter_set_name", length = 200)
    private String parameterSetName;

    /**
     * The values used, stored on the result rather than looked up. Editing or deleting the set
     * afterwards must not change what a past execution says it ran with.
     */
    @Column(name = "parameter_values_json", columnDefinition = "TEXT")
    private String parameterValuesJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_run_id", nullable = false)
    private TestRun testRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_case_id", nullable = false)
    private TestCase testCase;

    @OneToMany(mappedBy = "testResult", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StepResult> stepResults = new ArrayList<>();
}
