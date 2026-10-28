package com.deanmanagement.testmanagement.project.internal.dto.savedfilter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Rename, or share / unshare. The filter itself is replaced by saving a new one. */
public record UpdateSavedFilterRequest(
        @NotBlank @Size(max = 255) String name,
        boolean shared
) {
}
