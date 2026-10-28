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

import java.util.UUID;

/**
 * A list filter saved under a name (PRD-053 §3). The list's URL query string is its whole filter
 * state, so that string is what is stored. Private to its owner unless shared with the project.
 */
@Entity
@Table(name = "saved_filters")
@Getter
@Setter
@NoArgsConstructor
public class SavedFilter extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SavedFilterView view;

    @Column(name = "query_string", nullable = false, length = 4000)
    private String queryString;

    @Column(nullable = false)
    private boolean shared;
}
