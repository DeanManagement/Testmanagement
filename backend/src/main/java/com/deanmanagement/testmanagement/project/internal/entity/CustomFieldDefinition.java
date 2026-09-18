package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

import java.util.ArrayList;
import java.util.List;

/**
 * A typed attribute a project adds to its test cases, test runs or bug reports (PRD-035). Options
 * are read and written whole and never joined, so they live in one JSON column.
 */
@Entity
@Table(name = "custom_field_definitions")
@Getter
@Setter
@NoArgsConstructor
public class CustomFieldDefinition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomFieldEntityType entityType;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomFieldType fieldType;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "options_json", columnDefinition = "TEXT")
    private List<String> options = new ArrayList<>();

    @Column(nullable = false)
    private boolean required;

    @Column(nullable = false)
    private boolean archived;

    @Column(nullable = false)
    private int orderIndex;
}
