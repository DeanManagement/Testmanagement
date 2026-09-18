
package com.deanmanagement.testmanagement.project.internal.entity;

import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import com.deanmanagement.testmanagement.shared.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
public class Project extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "project_key", nullable = false, unique = true, length = 10)
    private String key;

    @Column(name = "next_test_case_number", nullable = false)
    private int nextTestCaseNumber = 1;

    @Column(name = "next_test_run_number", nullable = false)
    private int nextTestRunNumber = 1;

    @Column(name = "next_session_number", nullable = false)
    private int nextSessionNumber = 1;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProjectMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TestCase> testCases = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TestSuite> testSuites = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TestRun> testRuns = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TestPlan> testPlans = new ArrayList<>();

    @Column(name = "bug_reports_enabled", nullable = false)
    private boolean bugReportsEnabled = false;

    /** PRD-033: when on, ACTIVE means approved and needs the approve action. */
    @Column(name = "review_required", nullable = false)
    private boolean reviewRequired = false;

    /** Least role that may approve or request changes; ADMIN or TESTER. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reviewer_min_role", nullable = false, length = 20)
    private ProjectRole reviewerMinRole = ProjectRole.ADMIN;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BugReport> bugReports = new ArrayList<>();
}
