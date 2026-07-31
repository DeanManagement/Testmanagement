package com.deanmanagement.testmanagement.project.internal.dto.notification;

import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import jakarta.validation.constraints.NotNull;

/** A per-action notification preference; used for both GET and PUT. */
public record NotificationPreferenceDto(
        @NotNull AuditAction action,
        boolean inApp,
        boolean email
) {
}
