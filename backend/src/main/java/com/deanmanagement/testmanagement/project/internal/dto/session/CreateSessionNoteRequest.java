package com.deanmanagement.testmanagement.project.internal.dto.session;

import com.deanmanagement.testmanagement.project.internal.entity.SessionNoteType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** @param occurredAt defaults to now; may be back-dated, but not before the session started. */
public record CreateSessionNoteRequest(
        @NotNull SessionNoteType type,
        @NotBlank @Size(max = 10000) String body,
        Instant occurredAt
) {
}
