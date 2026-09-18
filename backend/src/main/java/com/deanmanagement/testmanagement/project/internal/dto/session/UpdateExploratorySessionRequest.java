package com.deanmanagement.testmanagement.project.internal.dto.session;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.UUID;

/**
 * Every field is optional; null leaves it unchanged. {@code environment: ""} clears the
 * environment. {@code summary} can be edited after completion, for late debrief additions.
 */
public record UpdateExploratorySessionRequest(
        String charter,
        @Min(ExploratorySessionLimits.MIN_TIMEBOX) @Max(ExploratorySessionLimits.MAX_TIMEBOX) Integer timeboxMinutes,
        UUID testPlanId,
        String environment,
        UUID environmentId,
        UUID testerId,
        String summary
) {
}
