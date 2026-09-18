package com.deanmanagement.testmanagement.project.internal.dto.session;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** @param environment a name, resolved against the catalogue like runs (PRD-032); the id wins. */
public record CreateExploratorySessionRequest(
        @NotBlank String charter,
        @NotNull @Min(ExploratorySessionLimits.MIN_TIMEBOX) @Max(ExploratorySessionLimits.MAX_TIMEBOX)
        Integer timeboxMinutes,
        UUID testPlanId,
        String environment,
        UUID environmentId,
        UUID testerId
) {
}
