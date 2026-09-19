package com.deanmanagement.testmanagement.project.internal.dto.sharedStep;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Creates a shared block, or replaces one's title, description and steps. */
public record SaveSharedStepRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        @NotNull @Valid List<SharedStepStepRequest> steps
) {
}
