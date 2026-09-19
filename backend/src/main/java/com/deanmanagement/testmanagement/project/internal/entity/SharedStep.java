package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A named block of steps kept once per project and referenced from test cases (PRD-030). Its steps
 * are ordinary {@link TestStep} rows owned by the block instead of a case.
 */
@Entity
@Table(name = "shared_steps")
@Getter
@Setter
@NoArgsConstructor
public class SharedStep extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "sharedStep", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<TestStep> steps = new ArrayList<>();
}
