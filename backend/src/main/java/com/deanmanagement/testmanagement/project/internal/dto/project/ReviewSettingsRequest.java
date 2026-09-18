package com.deanmanagement.testmanagement.project.internal.dto.project;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import jakarta.validation.constraints.NotNull;

/**
 * PRD-033 review switch. {@code reviewerMinRole} is ADMIN or TESTER; null keeps the current one.
 */
public record ReviewSettingsRequest(@NotNull Boolean reviewRequired, ProjectRole reviewerMinRole) {
}
