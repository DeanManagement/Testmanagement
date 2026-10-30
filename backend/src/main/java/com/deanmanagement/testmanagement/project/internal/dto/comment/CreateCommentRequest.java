package com.deanmanagement.testmanagement.project.internal.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** {@code mentionedUserIds}: project members to notify (TM44); bug report comments only. */
public record CreateCommentRequest(
        @NotBlank @Size(max = 2000) String content,
        @Size(max = 20) List<UUID> mentionedUserIds
) {
    public CreateCommentRequest(String content) {
        this(content, null);
    }

    public List<UUID> mentions() {
        return mentionedUserIds == null ? List.of() : mentionedUserIds;
    }
}
