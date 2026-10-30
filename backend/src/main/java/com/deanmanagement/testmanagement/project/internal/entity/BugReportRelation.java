package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;
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

/** {@code bugReport} {@code type} {@code relatedBug}, e.g. "BUG-3 BLOCKS BUG-7" (TM46). */
@Entity
@Table(name = "bug_report_relations")
@Getter
@Setter
@NoArgsConstructor
public class BugReportRelation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bug_report_id", nullable = false)
    private BugReport bugReport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_bug_id", nullable = false)
    private BugReport relatedBug;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 20)
    private BugRelationType type;
}
