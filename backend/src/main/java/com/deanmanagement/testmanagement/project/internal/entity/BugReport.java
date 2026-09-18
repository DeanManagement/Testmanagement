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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bug_reports")
@Getter
@Setter
@NoArgsConstructor
public class BugReport extends BaseEntity {

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
