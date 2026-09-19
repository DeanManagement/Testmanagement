package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import com.deanmanagement.testmanagement.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "test_plans")
@Getter
@Setter
@NoArgsConstructor
public class TestPlan extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestPlanStatus status;

    private LocalDate targetDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    /** Creation order, which is key order: run numbers are handed out sequentially (PRD-052). */
    @OneToMany(mappedBy = "testPlan")
    @OrderBy("createdAt ASC")
    private List<TestRun> testRuns = new ArrayList<>();

    // PRD-037 release gate. Null means that criterion is not used; see ReleaseGate.
    @Column(name = "gate_min_pass_rate", precision = 5, scale = 2)
    private BigDecimal gateMinPassRate;

    @Column(name = "gate_max_blocker_bugs")
    private Integer gateMaxBlockerBugs;

    @Column(name = "gate_min_coverage", precision = 5, scale = 2)
    private BigDecimal gateMinCoverage;

    @Column(name = "gate_max_flaky")
    private Integer gateMaxFlaky;
}
