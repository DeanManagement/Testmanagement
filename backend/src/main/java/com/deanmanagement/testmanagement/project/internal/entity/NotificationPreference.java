package com.deanmanagement.testmanagement.project.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Per-user, per-action notification opt-out. An absent row means defaults: in-app on, email off.
 */
@Entity
@Table(name = "notification_preferences", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "action"})
})
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    @Column(name = "in_app", nullable = false)
    private boolean inApp = true;

    @Column(nullable = false)
    private boolean email = false;
}
