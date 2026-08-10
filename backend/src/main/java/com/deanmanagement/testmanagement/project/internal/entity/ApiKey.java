package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;

import com.deanmanagement.testmanagement.user.User;

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

import java.time.Instant;

@Entity
@Table(name = "api_keys")
@Getter
@Setter
@NoArgsConstructor
public class ApiKey extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 8)
    private String keyPrefix;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    /** When the secret was last replaced. Null means the key still carries the one it was issued with. */
    @Column(name = "rotated_at")
    private Instant rotatedAt;

    /**
     * PRD-021 §4.2: the project this key may ingest into. {@code null} = legacy/global key
     * (deprecated — accepted with a startup warning, to be rejected in a future release).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    /**
     * PRD-025 §3.2: the role this key holds on its project. {@code VIEWER} or {@code TESTER} only —
     * {@code ADMIN} is not offered, so a key can never manage members or delete a project.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectRole role = ProjectRole.TESTER;

    /**
     * The service user this key authenticates as. {@code null} only for legacy project-less keys,
     * which have no project to hold a membership on.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_user_id")
    private User serviceUser;
}
