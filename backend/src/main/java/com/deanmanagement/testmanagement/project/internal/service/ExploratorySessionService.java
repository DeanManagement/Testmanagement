package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.access.ProjectAccessService;
import com.deanmanagement.testmanagement.project.internal.dto.session.CreateExploratorySessionRequest;
import com.deanmanagement.testmanagement.project.internal.dto.session.CreateSessionNoteRequest;
import com.deanmanagement.testmanagement.project.internal.dto.session.ExploratorySessionResponse;
import com.deanmanagement.testmanagement.project.internal.dto.session.SessionListFilter;
import com.deanmanagement.testmanagement.project.internal.dto.session.SessionNoteResponse;
import com.deanmanagement.testmanagement.project.internal.dto.session.UpdateExploratorySessionRequest;
import com.deanmanagement.testmanagement.project.internal.dto.session.UpdateSessionNoteRequest;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.ExploratorySession;
import com.deanmanagement.testmanagement.project.internal.entity.ExploratorySessionNote;
import com.deanmanagement.testmanagement.project.internal.entity.ExploratorySessionNoteImage;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlan;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ExploratorySessionNoteImageRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ExploratorySessionNoteRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ExploratorySessionRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestPlanRepository;
import com.deanmanagement.testmanagement.shared.exception.ForbiddenException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.UserService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Exploratory testing sessions (PRD-034). Every child id in a request (plan, tester,
 * environment, note, image) is looked up within the session's project, so an id from another
 * project is a 404 (PRD-027 §3.5).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExploratorySessionService {

    /** Notes may still be added this long after completion, for late debrief additions. */
    static final Duration LATE_NOTE_WINDOW = Duration.ofHours(24);
    /** Clock skew tolerated on a client-supplied occurredAt. */
    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(1);

    private final ExploratorySessionRepository sessionRepository;
    private final ExploratorySessionNoteRepository noteRepository;
    private final ExploratorySessionNoteImageRepository imageRepository;
    private final ProjectRepository projectRepository;
    private final TestPlanRepository testPlanRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final BugReportRepository bugReportRepository;
    private final ProjectSequenceService projectSequenceService;
    private final ProjectEnvironmentService environmentService;
    private final ProjectAccessService accessService;
    private final UserService userService;
    private final AuditService auditService;
    private final Clock clock;

    public Page<ExploratorySessionResponse> list(UUID projectId, SessionListFilter filter, Pageable pageable) {
        return sessionRepository.findAll(specification(projectId, filter), pageable).map(this::toSummary);
    }

    /** The caller's planned and running sessions across projects, for "My test runs". */
    public List<ExploratorySessionResponse> listActiveFor(UUID testerId) {
        return sessionRepository.findByTesterAndStatuses(testerId,
                        EnumSet.of(TestRunStatus.PLANNED, TestRunStatus.IN_PROGRESS)).stream()
                .map(this::toSummary)
                .toList();
    }

    public ExploratorySessionResponse get(UUID projectId, UUID id) {
        return toDetail(require(projectId, id));
    }

    @Transactional
    public ExploratorySessionResponse create(UUID projectId, CreateExploratorySessionRequest request, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        ExploratorySession session = new ExploratorySession();
        session.setProject(project);
        session.setCharter(requireText(request.charter(), "charter"));
        session.setTimeboxMinutes(request.timeboxMinutes());
        session.setStatus(TestRunStatus.PLANNED);
        if (request.testPlanId() != null) {
            session.setTestPlan(requirePlan(projectId, request.testPlanId()));
        }
        session.setEnvironment(environmentService.resolve(projectId, request.environmentId(), request.environment()));
        if (request.testerId() != null) {
            session.setTester(requireMember(projectId, request.testerId()));
        }
        session.setKey(project.getKey() + "-Session-" + projectSequenceService.nextSessionNumber(projectId));
        session = sessionRepository.save(session);
        audit(session, userId, AuditAction.CREATED, null);
        return toDetail(session);
    }

    @Transactional
    public ExploratorySessionResponse update(UUID projectId, UUID id, UpdateExploratorySessionRequest request,
                                             UUID userId) {
        ExploratorySession session = require(projectId, id);
        if (request.charter() != null) {
            session.setCharter(requireText(request.charter(), "charter"));
        }
        if (request.timeboxMinutes() != null) {
            session.setTimeboxMinutes(request.timeboxMinutes());
        }
        if (request.testPlanId() != null) {
            session.setTestPlan(requirePlan(projectId, request.testPlanId()));
        }
        if (request.environmentId() != null || request.environment() != null) {
            session.setEnvironment(environmentService.resolve(projectId, request.environmentId(), request.environment()));
        }
        if (request.testerId() != null) {
            session.setTester(requireMember(projectId, request.testerId()));
        }
        if (request.summary() != null) {
            session.setSummary(request.summary());
        }
        audit(session, userId, AuditAction.UPDATED, null);
        return toDetail(sessionRepository.save(session));
    }

    @Transactional
    public ExploratorySessionResponse start(UUID projectId, UUID id, UUID userId) {
        ExploratorySession session = require(projectId, id);
        requireStatus(session, TestRunStatus.PLANNED, "started");
        session.setStatus(TestRunStatus.IN_PROGRESS);
        session.setStartedAt(clock.instant());
        audit(session, userId, AuditAction.STATUS_CHANGED, "started");
        return toDetail(sessionRepository.save(session));
    }

    @Transactional
    public ExploratorySessionResponse complete(UUID projectId, UUID id, String summary, UUID userId) {
        return end(projectId, id, TestRunStatus.COMPLETED, summary, userId);
    }

    @Transactional
    public ExploratorySessionResponse abort(UUID projectId, UUID id, String summary, UUID userId) {
        return end(projectId, id, TestRunStatus.ABORTED, summary, userId);
    }

    @Transactional
    public void delete(UUID projectId, UUID id, UUID userId) {
        ExploratorySession session = require(projectId, id);
        audit(session, userId, AuditAction.DELETED, null);
        // Linked bug reports stay, unlinked. Done here rather than left to ON DELETE SET NULL, so a
        // bug already loaded in this transaction doesn't still point at the deleted session.
        bugReportRepository.findByExploratorySessionIdOrderByCreatedAtAsc(id)
                .forEach(bug -> bug.setExploratorySession(null));
        // Notes and their images go with it (ON DELETE CASCADE).
        sessionRepository.delete(session);
    }

    // ---- notes ------------------------------------------------------------------------------

    @Transactional
    public SessionNoteResponse addNote(UUID projectId, UUID sessionId, CreateSessionNoteRequest request) {
        ExploratorySession session = require(projectId, sessionId);
        requireAcceptingNotes(session);
        Instant now = clock.instant();
        Instant occurredAt = request.occurredAt() != null ? request.occurredAt() : now;
        if (occurredAt.isBefore(session.getStartedAt()) || occurredAt.isAfter(now.plus(FUTURE_TOLERANCE))) {
            throw new IllegalArgumentException("occurredAt must be between the session start and now");
        }
        ExploratorySessionNote note = new ExploratorySessionNote();
        note.setSession(session);
        note.setType(request.type());
        note.setBody(requireText(request.body(), "body"));
        note.setOccurredAt(occurredAt);
        return toNote(noteRepository.save(note), false);
    }

    @Transactional
    public SessionNoteResponse updateNote(UUID projectId, UUID sessionId, UUID noteId,
                                          UpdateSessionNoteRequest request, UUID userId) {
        ExploratorySessionNote note = requireOwnNote(projectId, sessionId, noteId, userId);
        if (request.type() != null) {
            note.setType(request.type());
        }
        if (request.body() != null) {
            note.setBody(requireText(request.body(), "body"));
        }
        return toNote(noteRepository.save(note), imageRepository.findByNoteId(noteId).isPresent());
    }

    @Transactional
    public void deleteNote(UUID projectId, UUID sessionId, UUID noteId, UUID userId) {
        noteRepository.delete(requireOwnNote(projectId, sessionId, noteId, userId));
    }

    // ---- note images ------------------------------------------------------------------------

    /** Replaces the note's image if it has one: at most one per note. */
    @Transactional
    public void uploadImage(UUID projectId, UUID sessionId, UUID noteId, String fileName, String contentType,
                            byte[] data, UUID userId) {
        ExploratorySessionNote note = requireOwnNote(projectId, sessionId, noteId, userId);
        String allowedType = ImageMediaTypes.requireAllowed(contentType);
        ExploratorySessionNoteImage image = imageRepository.findByNoteId(noteId).orElseGet(() -> {
            ExploratorySessionNoteImage created = new ExploratorySessionNoteImage();
            created.setNote(note);
            return created;
        });
        image.setFileName(fileName != null && !fileName.isBlank() ? fileName : "screenshot");
        image.setContentType(allowedType);
        image.setData(data);
        imageRepository.save(image);
    }

    public ExploratorySessionNoteImage getImage(UUID projectId, UUID sessionId, UUID noteId) {
        requireNote(projectId, sessionId, noteId);
        return imageRepository.findByNoteId(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("SessionNoteImage", noteId));
    }

    @Transactional
    public void deleteImage(UUID projectId, UUID sessionId, UUID noteId, UUID userId) {
        requireOwnNote(projectId, sessionId, noteId, userId);
        imageRepository.findByNoteId(noteId).ifPresent(imageRepository::delete);
    }

    // ---- lookups and rules ------------------------------------------------------------------

    ExploratorySession require(UUID projectId, UUID id) {
        return sessionRepository.findByIdAndProjectId(id, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("ExploratorySession", id));
    }

    private ExploratorySessionNote requireNote(UUID projectId, UUID sessionId, UUID noteId) {
        require(projectId, sessionId);
        return noteRepository.findByIdAndSessionId(noteId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("SessionNote", noteId));
    }

    /** Notes are edited by their author; a project admin may edit or remove anyone's. */
    private ExploratorySessionNote requireOwnNote(UUID projectId, UUID sessionId, UUID noteId, UUID userId) {
        ExploratorySessionNote note = requireNote(projectId, sessionId, noteId);
        boolean author = userId != null && userId.equals(note.getCreatedBy());
        if (!author && !isAdmin(projectId, userId)) {
            throw new ForbiddenException("Only the note's author or a project admin can change it");
        }
        return note;
    }

    private boolean isAdmin(UUID projectId, UUID userId) {
        if (userId == null) {
            return false;
        }
        try {
            return accessService.requireMember(userId, projectId).satisfies(ProjectRole.ADMIN);
        } catch (ForbiddenException notAMember) {
            return false;
        }
    }

    private void requireAcceptingNotes(ExploratorySession session) {
        boolean running = session.getStatus() == TestRunStatus.IN_PROGRESS;
        boolean lateDebrief = session.getStatus() == TestRunStatus.COMPLETED
                && session.getEndedAt().plus(LATE_NOTE_WINDOW).isAfter(clock.instant());
        if (!running && !lateDebrief) {
            throw new IllegalArgumentException(
                    "Notes can be added while the session runs, or up to 24 hours after it completes");
        }
    }

    private ExploratorySessionResponse end(UUID projectId, UUID id, TestRunStatus status, String summary,
                                           UUID userId) {
        ExploratorySession session = require(projectId, id);
        requireStatus(session, TestRunStatus.IN_PROGRESS, status == TestRunStatus.COMPLETED ? "completed" : "aborted");
        session.setStatus(status);
        session.setEndedAt(clock.instant());
        if (summary != null && !summary.isBlank()) {
            session.setSummary(summary);
        }
        audit(session, userId, AuditAction.STATUS_CHANGED, status.name().toLowerCase());
        return toDetail(sessionRepository.save(session));
    }

    private static void requireStatus(ExploratorySession session, TestRunStatus expected, String action) {
        if (session.getStatus() != expected) {
            throw new IllegalArgumentException("A " + session.getStatus() + " session can't be " + action);
        }
    }

    private TestPlan requirePlan(UUID projectId, UUID planId) {
        return testPlanRepository.findByIdAndProjectId(planId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("TestPlan", planId));
    }

    private User requireMember(UUID projectId, UUID userId) {
        if (!projectMemberRepository.existsByUserIdAndProjectId(userId, projectId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        return userService.findEntityById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private void audit(ExploratorySession session, UUID userId, AuditAction action, String details) {
        auditService.log(session.getProject().getId(), userId, action, AuditEntityType.EXPLORATORY_SESSION,
                session.getId(), session.getKey(), details);
    }

    private static Specification<ExploratorySession> specification(UUID projectId, SessionListFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("project").get("id"), projectId));
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.testPlanId() != null) {
                predicates.add(cb.equal(root.get("testPlan").get("id"), filter.testPlanId()));
            }
            if (filter.testerId() != null) {
                predicates.add(cb.equal(root.get("tester").get("id"), filter.testerId()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ---- mapping ----------------------------------------------------------------------------

    private ExploratorySessionResponse toSummary(ExploratorySession session) {
        return toResponse(session, null, null);
    }

    private ExploratorySessionResponse toDetail(ExploratorySession session) {
        List<ExploratorySessionNote> notes = session.getId() == null ? List.of()
                : noteRepository.findBySessionIdOrderByOccurredAtDescCreatedAtDesc(session.getId());
        Set<UUID> withImage = notes.isEmpty() ? Set.of()
                : imageRepository.findNoteIdsWithImage(notes.stream().map(ExploratorySessionNote::getId).toList());
        List<SessionNoteResponse> noteResponses = notes.stream()
                .map(note -> toNote(note, withImage.contains(note.getId())))
                .toList();
        List<ExploratorySessionResponse.LinkedBug> bugs = session.getId() == null ? List.of()
                : bugReportRepository.findByExploratorySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                        .map(b -> new ExploratorySessionResponse.LinkedBug(b.getId(), b.getTitle(), b.getStatus().name()))
                        .toList();
        return toResponse(session, noteResponses, bugs);
    }

    private static ExploratorySessionResponse toResponse(ExploratorySession s, List<SessionNoteResponse> notes,
                                                         List<ExploratorySessionResponse.LinkedBug> bugs) {
        return new ExploratorySessionResponse(
                s.getId(), s.getKey(), s.getProject().getId(), s.getProject().getKey(), s.getCharter(),
                s.getTimeboxMinutes(), s.getStatus(),
                s.getTestPlan() != null ? s.getTestPlan().getId() : null,
                s.getTestPlan() != null ? s.getTestPlan().getName() : null,
                s.getEnvironment() != null ? s.getEnvironment().getId() : null,
                s.getEnvironment() != null ? s.getEnvironment().getName() : null,
                s.getTester() != null ? s.getTester().getId() : null,
                s.getTester() != null ? s.getTester().getDisplayName() : null,
                s.getStartedAt(), s.getEndedAt(), s.getSummary(), s.getCreatedAt(), s.getUpdatedAt(),
                notes, bugs);
    }

    private static SessionNoteResponse toNote(ExploratorySessionNote note, boolean hasImage) {
        return new SessionNoteResponse(note.getId(), note.getType(), note.getBody(), note.getOccurredAt(),
                note.getCreatedBy(), hasImage);
    }
}
