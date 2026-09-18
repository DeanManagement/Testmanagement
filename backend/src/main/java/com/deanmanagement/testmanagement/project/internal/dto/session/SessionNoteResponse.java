package com.deanmanagement.testmanagement.project.internal.dto.session;

import com.deanmanagement.testmanagement.project.internal.entity.SessionNoteType;

import java.time.Instant;
import java.util.UUID;

public record SessionNoteResponse(
        UUID id,
        SessionNoteType type,
        String body,
        Instant occurredAt,
        UUID createdBy,
        boolean hasImage
) {
}
