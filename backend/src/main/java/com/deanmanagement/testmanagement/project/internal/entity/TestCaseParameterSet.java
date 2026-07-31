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
 * One row of data a parameterized test case runs against (PRD-015).
 *
 * <p>A case with no sets behaves exactly as it always has — expansion only happens when sets exist,
 * so this feature is invisible to every case that does not use it.
 */
@Entity
@Table(name = "test_case_parameter_sets")
@Getter
@Setter
@NoArgsConstructor
public class TestCaseParameterSet extends BaseEntity {

    @Column(name = "test_case_id", nullable = false)
    private UUID testCaseId;

    /** Shown on the expanded result so a tester knows which row they are executing. */
    @Column(nullable = false, length = 200)
    private String name;

    /** JSON object of {@code {key: value}}, substituted into {@code {key}} placeholders. */
    @Column(name = "values_json", nullable = false, columnDefinition = "TEXT")
    private String valuesJson;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;
}
