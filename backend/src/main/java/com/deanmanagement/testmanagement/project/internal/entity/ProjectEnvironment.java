package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A named place tests run against, e.g. {@code Chrome · Staging} (PRD-032). Names are unique per
 * project ignoring case and surrounding whitespace, which {@link #normalize} defines.
 */
@Entity
@Table(name = "project_environments")
@Getter
@Setter
@NoArgsConstructor
public class ProjectEnvironment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String name;

    @Setter(AccessLevel.NONE)
    @Column(name = "name_normalized", nullable = false)
    private String nameNormalized;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int sortOrder;

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
