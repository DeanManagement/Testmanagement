package com.deanmanagement.testmanagement.project.internal.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank @Size(max = 2000) String content
) {
}
