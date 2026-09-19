package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "test_steps")
@Getter
@Setter
@NoArgsConstructor
public class TestStep extends BaseEntity {

    @Column(nullable = false, columnDefinition = "TEXT")
    private String action;

    @Column(columnDefinition = "TEXT")
    private String expectedResult;

    @Column(columnDefinition = "TEXT")
    private String testData;

    @Column(nullable = false)
    private int orderIndex;

    /** The case this step belongs to; null for a step of a shared block (PRD-030). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_case_id")
    private TestCase testCase;

    /** The shared block this step belongs to; null for a case's own step. Exactly one owner is set. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_step_id")
    private SharedStep sharedStep;

    /**
     * On a case step: the block this step stands for. Its action holds the block title as of the
     * last save, a readable fallback; expected result, test data and image are not used.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uses_shared_step_id")
    private SharedStep usesSharedStep;

    @OneToOne(mappedBy = "testStep", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private StepImage image;
}
