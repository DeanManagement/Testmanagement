package com.deanmanagement.testmanagement.project.internal.dto.session;

import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An exploratory session (PRD-034). {@code notes} and {@code bugs} are only filled in by the
 * single-session read; lists leave them out.
 */
public record ExploratorySessionResponse(
        UUID id,
        String key,
        UUID projectId,
        String projectKey,
        String charter,
        int timeboxMinutes,
        TestRunStatus status,
        UUID testPlanId,
        String testPlanName,
        UUID environmentId,
        String environment,
        UUID testerId,
        String testerName,
        Instant startedAt,
        Instant endedAt,
        String summary,
        Instant createdAt,
        Instant updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<SessionNoteResponse> notes,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<LinkedBug> bugs
) {

    /** A bug report filed from this session. */
    public record LinkedBug(UUID id, String title, String status) {
    }
}
