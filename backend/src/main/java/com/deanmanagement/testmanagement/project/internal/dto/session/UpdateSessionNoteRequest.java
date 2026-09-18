package com.deanmanagement.testmanagement.project.internal.dto.session;

import com.deanmanagement.testmanagement.project.internal.entity.SessionNoteType;
import jakarta.validation.constraints.Size;

/** Null leaves a field unchanged. */
public record UpdateSessionNoteRequest(SessionNoteType type, @Size(max = 10000) String body) {
}
