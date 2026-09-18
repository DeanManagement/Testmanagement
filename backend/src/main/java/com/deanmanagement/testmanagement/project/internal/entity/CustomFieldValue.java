package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One stored value of a custom field (PRD-035). Exactly one owner is set, which the database
 * checks. A MULTI_SELECT field has one row per selected option; every other type has one row.
 */
@Entity
@Table(name = "custom_field_values")
@Getter
@Setter
@NoArgsConstructor
public class CustomFieldValue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id", nullable = false)
    private CustomFieldDefinition field;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_case_id")
    private TestCase testCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_run_id")
    private TestRun testRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bug_report_id")
    private BugReport bugReport;

    @Column(length = 500)
    private String valueText;

    @Column(precision = 19, scale = 4)
    private BigDecimal valueNumber;

    private LocalDate valueDate;
}
