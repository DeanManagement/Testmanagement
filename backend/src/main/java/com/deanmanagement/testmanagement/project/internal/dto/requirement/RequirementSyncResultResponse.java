package com.deanmanagement.testmanagement.project.internal.dto.requirement;

/**
 * What a sync did, or with {@code dryRun} would do (TM19). {@code obsoleted} counts requirements
 * marked OBSOLETE because their work item left the query; {@code unchanged} those already current.
 */
public record RequirementSyncResultResponse(int created, int updated, int obsoleted, int unchanged, boolean dryRun) {
}
