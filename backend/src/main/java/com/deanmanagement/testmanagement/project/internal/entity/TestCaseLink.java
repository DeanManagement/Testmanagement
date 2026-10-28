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

/** A typed link from one test case to another in the same project (TM07). */
@Entity
@Table(name = "test_case_links")
@Getter
@Setter
@NoArgsConstructor
public class TestCaseLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_case_id", nullable = false)
    private TestCase source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_case_id", nullable = false)
    private TestCase target;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 20)
    private TestCaseLinkType linkType;
}
