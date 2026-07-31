package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Something the product must do, that tests are meant to prove (PRD-014).
 *
 * <p>Deliberately thin: an id from wherever the requirement really lives, a title, and a
 * description. This is not an attempt to be a requirements-management tool — it exists so coverage
 * can be demonstrated, and anything richer belongs in the system of record.
 */
@Entity
@Table(name = "requirements")
@Getter
@Setter
@NoArgsConstructor
public class Requirement extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** The identifier used in the spec or tracker this requirement came from, e.g. {@code REQ-014}. */
    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "requirement_test_cases",
            joinColumns = @JoinColumn(name = "requirement_id"),
            inverseJoinColumns = @JoinColumn(name = "test_case_id"))
    private Set<TestCase> testCases = new HashSet<>();
}
