package com.deanmanagement.testmanagement.project.internal.dto.requirement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * TM19 sync settings. {@code query} is WIQL; blank state lists fall back to the defaults, a blank
 * prefix means the bare work item id. {@code enabled} and {@code scheduled} default to false.
 */
public record SaveRequirementSyncConfigRequest(
        Boolean enabled,
        Boolean scheduled,
        @NotBlank @Size(max = 4000) String query,
        @Size(max = 20) String externalIdPrefix,
        @Size(max = 1000) String approvedStates,
        @Size(max = 1000) String obsoleteStates
) {
}
