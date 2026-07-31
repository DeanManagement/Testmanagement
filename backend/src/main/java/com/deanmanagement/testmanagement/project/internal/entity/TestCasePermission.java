package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Per-user override on a single test case. Grants a project member edit rights on a test case they
 * would not otherwise be able to change, or records explicit view-only access. Project-level roles
 * (see {@code ProjectAccessService}) remain the primary authorization mechanism; this is an
 * additive exception, never a restriction.
 */
@Entity
@Table(name = "test_case_permissions")
@Getter
@Setter
@NoArgsConstructor
public class TestCasePermission extends BaseEntity {

    @Column(name = "test_case_id", nullable = false)
    private UUID testCaseId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** {@code true} grants edit (which implies view); {@code false} is view-only. */
    @Column(name = "can_edit", nullable = false)
    private boolean canEdit;
}
