package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.session.CreateExploratorySessionRequest;
import com.deanmanagement.testmanagement.project.internal.dto.session.CreateSessionNoteRequest;
import com.deanmanagement.testmanagement.project.internal.dto.session.ExploratorySessionResponse;
import com.deanmanagement.testmanagement.project.internal.dto.session.SessionListFilter;
import com.deanmanagement.testmanagement.project.internal.dto.session.SessionNoteResponse;
import com.deanmanagement.testmanagement.project.internal.dto.session.UpdateExploratorySessionRequest;
import com.deanmanagement.testmanagement.project.internal.entity.ExploratorySession;
import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.entity.SessionNoteType;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ExploratorySessionRepository;
import com.deanmanagement.testmanagement.project.internal.service.ExploratorySessionService;
import com.deanmanagement.testmanagement.shared.PageableUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Exploratory testing sessions (TM36, PRD-034). Every rule — the status transitions, when notes are
 * accepted, image types — is {@link ExploratorySessionService}'s; the tools only translate. Roles
 * mirror the REST controller: reading needs any key, everything else TESTER.
 */
@Service
@InToolGroup(McpToolGroup.EXECUTION)
@RequiredArgsConstructor
public class ExploratorySessionTools {

    private final McpCallerContext callerContext;
    private final ExploratorySessionService sessionService;
    private final ExploratorySessionRepository sessionRepository;
    private final McpEnvironmentGuard environmentGuard;
    private final McpWriteThrottle writeThrottle;
    private final McpValidator validator;

    @McpTool(
            name = "list_exploratory_sessions",
            description = """
                    Exploratory testing sessions, newest first, without their notes (use
                    get_exploratory_session for those). status: PLANNED | IN_PROGRESS | COMPLETED |
                    ABORTED.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.ExploratorySessionPage listExploratorySessions(
            @McpToolParam(description = "Only sessions in this status", required = false) TestRunStatus status,
            @McpToolParam(description = "Only sessions under this test plan UUID", required = false) UUID testPlanId,
            @McpToolParam(description = "Zero-based page number, default 0", required = false) Integer page,
            @McpToolParam(description = "Page size, default 50, max 200", required = false) Integer size) {
        var caller = callerContext.require();
        var pageable = PageableUtils.normalize(PageRequest.of(
                page == null || page < 0 ? 0 : page,
                size == null || size < 1 ? PageableUtils.DEFAULT_SIZE : size,
                Sort.by(Sort.Direction.DESC, "createdAt")));
        Page<ExploratorySessionResponse> result = sessionService.list(caller.projectId(),
                new SessionListFilter(status, testPlanId, null), pageable);
        return new McpDtos.ExploratorySessionPage(result.getContent().stream().map(this::toDto).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.hasNext());
    }

    @McpTool(
            name = "get_exploratory_session",
            description = "One exploratory session with its notes (newest first) and the bug reports filed from it.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.ExploratorySession getExploratorySession(
            @McpToolParam(description = "Session key (PROJ-Session-3) or UUID") String session) {
        var caller = callerContext.require();
        return toDto(sessionService.get(caller.projectId(), sessionId(caller, session)));
    }

    @McpTool(
            name = "create_exploratory_session",
            description = """
                    Plan an exploratory session: a charter (the mission) and a timebox in minutes
                    (5 to 480). It is created PLANNED; call start_exploratory_session when testing
                    begins. environment must be one list_environments knows. Assigning a tester is
                    left to a human.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.ExploratorySession createExploratorySession(
            @McpToolParam(description = "What to explore and why") String charter,
            @McpToolParam(description = "Timebox in minutes, 5 to 480") Integer timeboxMinutes,
            @McpToolParam(description = "Test plan UUID to file it under", required = false) UUID testPlanId,
            @McpToolParam(description = "Environment name from list_environments", required = false)
            String environment) {
        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        environmentGuard.requireKnown(caller.projectId(), environment);
        var request = new CreateExploratorySessionRequest(charter, timeboxMinutes, testPlanId,
                environment, null, null);
        validator.validate(request);
        return toDto(sessionService.create(caller.projectId(), request, caller.userId()));
    }

    @McpTool(
            name = "update_exploratory_session",
            description = """
                    Change a session's charter, timebox, test plan, environment or debrief summary.
                    Only what you pass changes; environment "" clears it. The summary may still be
                    edited after the session ended.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.ExploratorySession updateExploratorySession(
            @McpToolParam(description = "Session key (PROJ-Session-3) or UUID") String session,
            @McpToolParam(description = "New charter", required = false) String charter,
            @McpToolParam(description = "New timebox in minutes, 5 to 480", required = false) Integer timeboxMinutes,
            @McpToolParam(description = "Test plan UUID", required = false) UUID testPlanId,
            @McpToolParam(description = "Environment name; \"\" clears it", required = false) String environment,
            @McpToolParam(description = "Debrief summary", required = false) String summary) {
        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        if (charter == null && timeboxMinutes == null && testPlanId == null && environment == null
                && summary == null) {
            throw new McpToolException(
                    "Nothing to update: pass charter, timeboxMinutes, testPlanId, environment or summary.");
        }
        environmentGuard.requireKnown(caller.projectId(), environment);
        var request = new UpdateExploratorySessionRequest(charter, timeboxMinutes, testPlanId, environment,
                null, null, summary);
        validator.validate(request);
        return toDto(sessionService.update(caller.projectId(), sessionId(caller, session), request,
                caller.userId()));
    }

    @McpTool(
            name = "start_exploratory_session",
            description = "Start a PLANNED session; notes can be added from then on.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.ExploratorySession startExploratorySession(
            @McpToolParam(description = "Session key (PROJ-Session-3) or UUID") String session) {
        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        return toDto(sessionService.start(caller.projectId(), sessionId(caller, session), caller.userId()));
    }

    @McpTool(
            name = "complete_exploratory_session",
            description = """
                    End a running session as COMPLETED, optionally with a debrief summary. Notes can
                    still be added for 24 hours afterwards.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.ExploratorySession completeExploratorySession(
            @McpToolParam(description = "Session key (PROJ-Session-3) or UUID") String session,
            @McpToolParam(description = "Debrief summary", required = false) String summary) {
        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        return toDto(sessionService.complete(caller.projectId(), sessionId(caller, session), summary,
                caller.userId()));
    }

    @McpTool(
            name = "abort_exploratory_session",
            description = "End a running session as ABORTED, optionally saying why in the summary.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.ExploratorySession abortExploratorySession(
            @McpToolParam(description = "Session key (PROJ-Session-3) or UUID") String session,
            @McpToolParam(description = "Why it was aborted", required = false) String summary) {
        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        return toDto(sessionService.abort(caller.projectId(), sessionId(caller, session), summary,
                caller.userId()));
    }

    @McpTool(
            name = "add_session_note",
            description = """
                    Log a note in a running session (or up to 24 hours after it completed).
                    type: NOTE | BUG | QUESTION | IDEA. Optionally attach one screenshot:
                    imageBase64 holds its bytes, at most 2 MB decoded, with imageContentType
                    image/png, image/jpeg, image/gif or image/webp.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.SessionNote addSessionNote(
            @McpToolParam(description = "Session key (PROJ-Session-3) or UUID") String session,
            @McpToolParam(description = "NOTE, BUG, QUESTION or IDEA") SessionNoteType type,
            @McpToolParam(description = "The note, max 10000 characters") String body,
            @McpToolParam(description = "Screenshot bytes, base64-encoded", required = false) String imageBase64,
            @McpToolParam(description = "Screenshot MIME type, e.g. image/png", required = false)
            String imageContentType,
            @McpToolParam(description = "Screenshot file name", required = false) String imageFileName) {
        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        UUID id = sessionId(caller, session);
        var request = new CreateSessionNoteRequest(type, body, null);
        validator.validate(request);
        byte[] image = imageBase64 == null ? null : BugReportTools.decodeAttachment(imageBase64);
        SessionNoteResponse note = sessionService.addNote(caller.projectId(), id, request);
        if (image != null) {
            sessionService.uploadImage(caller.projectId(), id, note.id(), imageFileName, imageContentType, image,
                    caller.userId());
        }
        return new McpDtos.SessionNote(note.id(), note.type(), note.body(), note.occurredAt(), image != null);
    }

    @McpTool(
            name = "delete_exploratory_session",
            description = """
                    Permanently delete a session with its notes and screenshots. Bug reports filed
                    from it are kept, unlinked. Cannot be undone.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, idempotentHint = false))
    @Transactional
    public McpDtos.Deleted deleteExploratorySession(
            @McpToolParam(description = "Session key (PROJ-Session-3) or UUID") String session) {
        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        ExploratorySessionResponse existing = sessionService.get(caller.projectId(), sessionId(caller, session));
        sessionService.delete(caller.projectId(), existing.id(), caller.userId());
        return new McpDtos.Deleted("EXPLORATORY_SESSION", existing.id(), existing.key());
    }

    /** A UUID or a session key, the key looked up within the caller's project only. */
    private UUID sessionId(McpCallerContext.Caller caller, String reference) {
        if (reference == null || reference.isBlank()) {
            throw new McpToolException("A session key or UUID is required, e.g. from list_exploratory_sessions.");
        }
        String trimmed = reference.strip();
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException notAUuid) {
            return sessionRepository.findByKeyAndProjectId(trimmed, caller.projectId())
                    .map(ExploratorySession::getId)
                    .orElseThrow(() -> new McpToolException("No exploratory session " + trimmed
                            + " in this project. Call list_exploratory_sessions to see what there is."));
        }
    }

    private McpDtos.ExploratorySession toDto(ExploratorySessionResponse s) {
        return new McpDtos.ExploratorySession(s.id(), s.key(), s.charter(), s.timeboxMinutes(), s.status(),
                s.testPlanId(), s.testPlanName(), s.environment(), s.testerName(), s.startedAt(), s.endedAt(),
                s.summary(),
                s.notes() == null ? null : s.notes().stream()
                        .map(n -> new McpDtos.SessionNote(n.id(), n.type(), n.body(), n.occurredAt(), n.hasImage()))
                        .toList(),
                s.bugs() == null ? null : s.bugs().stream()
                        .map(b -> new McpDtos.SessionBug(b.id(), b.title(), b.status()))
                        .toList());
    }
}
