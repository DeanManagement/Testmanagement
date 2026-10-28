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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Locale;

/**
 * A result status a project defined, e.g. "Not applicable" (TM24). It only names an outcome: the
 * {@link #category} is what every metric counts, and it is fixed once created, because results
 * store it in their own status column.
 */
@Entity
@Table(name = "project_result_statuses")
@Getter
@Setter
@NoArgsConstructor
public class ProjectResultStatus extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 100)
    private String name;

    @Setter(AccessLevel.NONE)
    @Column(name = "name_normalized", nullable = false, length = 100)
    private String nameNormalized;

    /** {@code #rrggbb}. */
    @Column(nullable = false, length = 7)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TestResultStatus category;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean archived;

    /** Sets the display name (trimmed) and keeps the uniqueness key in step with it. */
    public void setName(String name) {
        this.name = name.trim();
        this.nameNormalized = normalize(name);
    }

    public static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
