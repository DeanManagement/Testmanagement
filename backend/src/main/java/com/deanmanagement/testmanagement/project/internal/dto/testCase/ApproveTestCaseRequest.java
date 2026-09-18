package com.deanmanagement.testmanagement.project.internal.dto.testCase;

import jakarta.validation.constraints.NotNull;

/**
 * @param version the version the reviewer read; a case that moved on since is a 409 (PRD-033).
 * @param force   system admins only: approve despite being the author or last editor. Audited.
 */
public record ApproveTestCaseRequest(@NotNull Integer version, Boolean force) {
}
