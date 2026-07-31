package com.deanmanagement.testmanagement.project.internal.dto.comment;

import com.deanmanagement.testmanagement.project.internal.entity.CommentEntityType;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        String content,
        UUID authorId,
        String authorDisplayName,
        CommentEntityType entityType,
        UUID entityId,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy
) {
}
