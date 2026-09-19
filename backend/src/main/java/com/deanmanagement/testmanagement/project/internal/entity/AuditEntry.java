package com.deanmanagement.testmanagement.project.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_entries")
@Getter
@Setter
@NoArgsConstructor
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 50)
    private AuditEntityType entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "entity_name")
    private String entityName;

    @Column(columnDefinition = "TEXT")
    private String details;

    /** PRD-046: JSON array of {field, from, to}; null when the entry records no field changes. */
    @Column(columnDefinition = "TEXT")
    private String changes;

    /** PRD-046: the object this one belongs to, e.g. the test case of a comment. */
    @Enumerated(EnumType.STRING)
    @Column(name = "parent_entity_type", length = 50)
    private AuditEntityType parentEntityType;

    @Column(name = "parent_entity_id")
    private UUID parentEntityId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
