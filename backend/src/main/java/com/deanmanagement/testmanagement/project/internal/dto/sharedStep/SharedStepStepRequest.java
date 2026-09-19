package com.deanmanagement.testmanagement.project.internal.dto.sharedStep;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * One step of a shared block. {@code id} names an existing step to update in place; without it the
 * step is new. Kept in place so results recorded against it keep their text (PRD-030 §3.2).
 */
public record SharedStepStepRequest(
        UUID id,
        @NotBlank String action,
        String expectedResult,
        String testData
) {
}
