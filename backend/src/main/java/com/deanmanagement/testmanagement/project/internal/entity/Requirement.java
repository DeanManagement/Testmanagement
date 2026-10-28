package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Something the product must do, that tests are meant to prove (PRD-014).
 *
 * <p>An id from wherever the requirement really lives, a title and a description, plus (TM18) a
 * status, a priority and custom fields. Still not a requirements-management tool — it exists so
 * coverage can be demonstrated, and anything richer belongs in the system of record.
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequirementStatus status = RequirementStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority = Priority.MEDIUM;

    /** PRD-035 / TM18. Lazy; the global batch fetch size keeps list pages to one query per page. */
    @OneToMany(mappedBy = "requirement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomFieldValue> customFieldValues = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "requirement_test_cases",
            joinColumns = @JoinColumn(name = "requirement_id"),
            inverseJoinColumns = @JoinColumn(name = "test_case_id"))
    private Set<TestCase> testCases = new HashSet<>();
}
