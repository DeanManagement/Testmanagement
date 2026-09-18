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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A time-boxed exploratory testing session (PRD-034): a charter, a log of notes and a debrief.
 * Reuses {@link TestRunStatus} because the lifecycle is the same: PLANNED, IN_PROGRESS, then
 * COMPLETED or ABORTED.
 */
@Entity
@Table(name = "exploratory_sessions")
@Getter
@Setter
@NoArgsConstructor
public class ExploratorySession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_plan_id")
    private TestPlan testPlan;

    /** From the project's catalogue (PRD-032), so renames and merges carry through. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id")
    private ProjectEnvironment environment;

    @Column(name = "session_key", nullable = false, unique = true, length = 30)
    private String key;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String charter;

    @Column(name = "timebox_minutes", nullable = false)
    private int timeboxMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestRunStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tester_id")
    private User tester;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(columnDefinition = "TEXT")
    private String summary;
}
