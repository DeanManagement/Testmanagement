package com.deanmanagement.testmanagement.project.internal.dto.session;

import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;

import java.util.UUID;

/** Optional filters for the session list; null means "any". */
public record SessionListFilter(TestRunStatus status, UUID testPlanId, UUID testerId) {
}
