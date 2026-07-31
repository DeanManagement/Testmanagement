package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * An immutable snapshot of a test case as it stood before an edit (PRD-011).
 *
 * <p>Written before the change, not after, so "version N" names the wording that results stamped
 * with N actually executed. Snapshotting after the edit would shift every historical result by one.
 */
@Entity
@Table(name = "test_case_versions")
@Getter
@Setter
@NoArgsConstructor
public class TestCaseVersion extends BaseEntity {

    @Column(name = "test_case_id", nullable = false)
    private UUID testCaseId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    /** When this state stopped being current, i.e. when the edit that replaced it happened. */
    @Column(name = "version_at", nullable = false)
    private Instant versionAt;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String preconditions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestCaseStatus status;

    /** Comma-separated; labels are a flat set and this keeps the snapshot one row. */
    @Column(columnDefinition = "TEXT")
    private String labels;

    /** JSON array of {@code {action, expectedResult, testData, orderIndex}}. */
    @Column(name = "steps_snapshot", nullable = false, columnDefinition = "TEXT")
    private String stepsSnapshot;
}
