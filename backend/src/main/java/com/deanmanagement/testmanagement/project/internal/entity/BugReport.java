package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import com.deanmanagement.testmanagement.user.User;

import jakarta.persistence.CascadeType;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bug_reports")
@Getter
@Setter
@NoArgsConstructor
public class BugReport extends BaseEntity {

    /** {@code <PROJECT>-BUG-<n>} (PRD-045); stored, so a project key rename leaves old bugs as they were. */
    @Column(name = "bug_key", nullable = false, unique = true, length = 40)
    private String key;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String stepsToReproduce;

    @Column(columnDefinition = "TEXT")
    private String expectedBehavior;

    @Column(columnDefinition = "TEXT")
    private String actualBehavior;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BugReportStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BugResolution resolution;

    /** Set with resolution DUPLICATE: the bug this one repeats, in the same project. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_of_id")
    private BugReport duplicateOf;

    private String environment;

    /**
     * Source of truth for the environment (PRD-032); {@code environment} is a copy of its name
     * kept for the many read paths. Set both through {@link #assignEnvironment}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id")
    private ProjectEnvironment projectEnvironment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_result_id")
    private TestResult testResult;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_run_id")
    private TestRun testRun;

    /** PRD-047: the step of {@code testResult} it was found in, if the tester named one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_result_id")
    private StepResult stepResult;

    /** PRD-047: when it last became RESOLVED or CLOSED; cleared when reopened. */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** PRD-047: where else it showed up; the found-in result stays {@code testResult}. */
    @OneToMany(mappedBy = "bugReport", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt")
    private List<BugReportLink> links = new ArrayList<>();

    /** The exploratory session this bug was found in (PRD-034), if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exploratory_session_id")
    private ExploratorySession exploratorySession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    /** PRD-035. Lazy; the global batch fetch size keeps list pages to one query per page. */
    @OneToMany(mappedBy = "bugReport", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomFieldValue> customFieldValues = new ArrayList<>();

    public void assignEnvironment(ProjectEnvironment environment) {
        this.projectEnvironment = environment;
        this.environment = environment != null ? environment.getName() : null;
    }
}
