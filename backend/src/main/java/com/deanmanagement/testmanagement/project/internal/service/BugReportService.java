package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.repository.ExploratorySessionRepository;
import com.deanmanagement.testmanagement.project.internal.entity.ExploratorySession;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportFilter;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportMapper;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportResponse;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.ChangeBugStatusRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.CreateBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.UpdateBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.BugResolution;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import com.deanmanagement.testmanagement.project.internal.webhook.WebhookEvent;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.project.internal.repository.spec.BugReportSpecifications;
import com.deanmanagement.testmanagement.shared.exception.ForbiddenException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BugReportService {

    private final BugReportRepository bugReportRepository;
    private final ProjectRepository projectRepository;
    private final TestResultRepository testResultRepository;
    private final TestRunRepository testRunRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserService userService;
    private final BugReportMapper bugReportMapper;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final ProjectEnvironmentService environmentService;
    private final ExploratorySessionRepository exploratorySessionRepository;
    private final CustomFieldValueWriter customFieldWriter;
    private final ProjectSequenceService projectSequenceService;

    /** Every bug of the project, unpaged: for duplicate checks, not for display. */
    public List<BugReportResponse> findByProject(UUID projectId) {
        requireBugReportsEnabled(projectId);
        return toResponsesWithReporters(bugReportRepository.findByProjectIdWithDetails(projectId));
    }

    /** The bug list (PRD-045 §3.2): filtered, sorted and paged in the database. */
    public Page<BugReportResponse> search(UUID projectId, BugReportFilter filter, Pageable pageable) {
        requireBugReportsEnabled(projectId);
        Page<BugReport> page = bugReportRepository.findAll(
                BugReportSpecifications.build(projectId, filter), BugReportSort.toEntitySort(pageable));
        Map<UUID, String> reporterNames = reporterNames(page.getContent());
        return page.map(bug -> toResponse(bug, reporterNames));
    }

    /** By UUID or by key (PROJ-BUG-12), always within the project. */
    public BugReportResponse findById(UUID projectId, String idOrKey) {
        requireBugReportsEnabled(projectId);
        return toResponseWithReporter(require(projectId, idOrKey));
    }

    public BugReportResponse findById(UUID projectId, UUID id) {
        return findById(projectId, id.toString());
    }

    @Transactional
    public BugReportResponse create(UUID projectId, CreateBugReportRequest request, UUID userId) {
        return create(projectId, request, userId, CustomFieldWriteMode.INTERACTIVE);
    }

    /** {@code mode} decides whether required custom fields must be filled (PRD-035 §3.3). */
    @Transactional
    public BugReportResponse create(UUID projectId, CreateBugReportRequest request, UUID userId,
                                    CustomFieldWriteMode mode) {
        requireBugReportsEnabled(projectId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        BugReport bugReport = new BugReport();
        bugReport.setTitle(request.title());
        bugReport.setDescription(request.description());
        bugReport.setStepsToReproduce(request.stepsToReproduce());
        bugReport.setExpectedBehavior(request.expectedBehavior());
        bugReport.setActualBehavior(request.actualBehavior());
        bugReport.setPriority(request.priority());
        // New bugs wait for triage (PRD-045); OPEN means someone has confirmed it.
        bugReport.setStatus(BugReportStatus.NEW);
        bugReport.setProject(project);
        bugReport.setKey(project.getKey() + "-BUG-" + projectSequenceService.nextBugNumber(projectId));
        customFieldWriter.write(bugReport, request.customFields(), mode);
        UUID environmentId = request.environmentId();
        if (request.exploratorySessionId() != null) {
            ExploratorySession session = exploratorySessionRepository
                    .findByIdAndProjectId(request.exploratorySessionId(), projectId)
                    .orElseThrow(() -> new ResourceNotFoundException("ExploratorySession", request.exploratorySessionId()));
            bugReport.setExploratorySession(session);
            boolean namedEnvironment = environmentId != null
                    || (request.environment() != null && !request.environment().isBlank());
            if (!namedEnvironment && session.getEnvironment() != null) {
                environmentId = session.getEnvironment().getId();
            }
        }
        bugReport.assignEnvironment(environmentService.resolve(projectId, environmentId, request.environment()));

        if (request.testResultId() != null) {
            bugReport.setTestResult(requireTestResult(projectId, request.testResultId()));
        }
        bugReport.setTestRun(resolveTestRun(projectId, bugReport.getTestResult(), request.testRunId()));
        if (request.assigneeId() != null) {
            bugReport.setAssignee(requireProjectMember(projectId, request.assigneeId()));
        }

        bugReport = bugReportRepository.save(bugReport);
        auditService.log(projectId, userId, AuditAction.CREATED,
                AuditEntityType.BUG_REPORT, bugReport.getId(), label(bugReport), null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bugReportId", bugReport.getId().toString());
        data.put("key", bugReport.getKey());
        data.put("title", bugReport.getTitle());
        data.put("priority", bugReport.getPriority() != null ? bugReport.getPriority().name() : null);
        data.put("status", bugReport.getStatus().name());
        eventPublisher.publishEvent(new WebhookEvent(WebhookEventType.BUG_REPORT_CREATED, projectId, data));

        return toResponseWithReporter(bugReport);
    }

    @Transactional
    public BugReportResponse update(UUID projectId, UUID id, UpdateBugReportRequest request, UUID userId) {
        requireBugReportsEnabled(projectId);
        BugReport bugReport = require(projectId, id.toString());
        FieldChanges.Snapshot before = snapshot(bugReport);

        bugReport.setTitle(request.title());
        bugReport.setDescription(request.description());
        bugReport.setStepsToReproduce(request.stepsToReproduce());
        bugReport.setExpectedBehavior(request.expectedBehavior());
        bugReport.setActualBehavior(request.actualBehavior());
        bugReport.setPriority(request.priority());
        // The SPA sends the whole object, so null clears the environment like the other fields.
        bugReport.assignEnvironment(environmentService.resolve(projectId, request.environmentId(), request.environment()));

        // Null means "clear the link" here, deliberately — the SPA sends the whole object, so an
        // absent assignee is how a human unassigns. A supplied id that does not resolve is a
        // different thing entirely and now fails instead of silently clearing (PRD-027 §3.5).
        bugReport.setTestResult(request.testResultId() == null
                ? null : requireTestResult(projectId, request.testResultId()));
        bugReport.setTestRun(resolveTestRun(projectId, bugReport.getTestResult(), request.testRunId()));
        bugReport.setAssignee(request.assigneeId() == null
                ? null : requireProjectMember(projectId, request.assigneeId()));
        customFieldWriter.write(bugReport, request.customFields(), CustomFieldWriteMode.INTERACTIVE);

        // Flushed so JPA auditing stamps updatedBy before the response is built from it.
        bugReport = bugReportRepository.saveAndFlush(bugReport);
        auditService.log(projectId, userId, AuditAction.UPDATED, AuditEntityType.BUG_REPORT, bugReport.getId(),
                label(bugReport), null, FieldChanges.between(before, snapshot(bugReport)));
        return toResponseWithReporter(bugReport);
    }

    @Transactional
    public BugReportResponse changeStatus(UUID projectId, UUID id, ChangeBugStatusRequest request, UUID userId) {
        requireBugReportsEnabled(projectId);
        BugReport bugReport = require(projectId, id.toString());
        applyStatus(projectId, bugReport, request, userId);
        return toResponseWithReporter(bugReportRepository.saveAndFlush(bugReport));
    }

    /**
     * The status rules, shared by the single and the bulk change (PRD-045 §3.1): closing needs a
     * resolution, DUPLICATE needs another bug of the project, and reopening clears both.
     */
    void applyStatus(UUID projectId, BugReport bugReport, ChangeBugStatusRequest request, UUID userId) {
        BugResolution resolution = request.resolution();
        if (request.status() == BugReportStatus.CLOSED && resolution == null) {
            throw new IllegalArgumentException("Closing a bug needs a resolution: "
                    + Arrays.toString(BugResolution.values()));
        }
        if (resolution != null && request.status().isOpen()) {
            throw new IllegalArgumentException("A bug in status " + request.status()
                    + " has no resolution; only RESOLVED and CLOSED take one");
        }
        BugReport duplicateOf = resolveDuplicateTarget(projectId, bugReport, resolution, request.duplicateOfId());

        FieldChanges.Snapshot before = snapshot(bugReport);
        bugReport.setStatus(request.status());
        bugReport.setResolution(resolution);
        bugReport.setDuplicateOf(duplicateOf);

        // The fields carry "A -> B" (PRD-046); the reason is what only the person can say.
        auditService.log(projectId, userId, AuditAction.STATUS_CHANGED, AuditEntityType.BUG_REPORT,
                bugReport.getId(), label(bugReport), request.reason(),
                FieldChanges.between(before, snapshot(bugReport)));
    }

    private BugReport resolveDuplicateTarget(UUID projectId, BugReport bugReport, BugResolution resolution,
                                             UUID duplicateOfId) {
        if (resolution != BugResolution.DUPLICATE) {
            if (duplicateOfId != null) {
                throw new IllegalArgumentException("duplicateOfId goes with resolution DUPLICATE only");
            }
            return null;
        }
        if (duplicateOfId == null) {
            throw new IllegalArgumentException("Resolution DUPLICATE needs duplicateOfId: the bug this one repeats");
        }
        if (duplicateOfId.equals(bugReport.getId())) {
            throw new IllegalArgumentException("A bug cannot be a duplicate of itself");
        }
        return require(projectId, duplicateOfId.toString());
    }

    public List<BugReportResponse> findByAssignee(UUID assigneeId) {
        return toResponsesWithReporters(bugReportRepository.findByAssigneeIdWithDetails(assigneeId));
    }

    @Transactional
    public void delete(UUID projectId, UUID id, UUID userId) {
        requireBugReportsEnabled(projectId);
        delete(projectId, require(projectId, id.toString()), userId);
    }

    void delete(UUID projectId, BugReport bugReport, UUID userId) {
        auditService.log(projectId, userId, AuditAction.DELETED,
                AuditEntityType.BUG_REPORT, bugReport.getId(), label(bugReport), null);
        bugReportRepository.delete(bugReport);
    }

    /**
     * Fills a new report's empty fields from the project's template (PRD-045 §3.5). Used where no
     * form showed the template first; an agent's own text is never overwritten.
     */
    public CreateBugReportRequest withTemplate(UUID projectId, CreateBugReportRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        boolean namesEnvironment = request.environmentId() != null || !isBlank(request.environment());
        return new CreateBugReportRequest(request.title(),
                orTemplate(request.description(), project.getBugTemplateDescription()),
                orTemplate(request.stepsToReproduce(), project.getBugTemplateSteps()),
                request.expectedBehavior(), request.actualBehavior(), request.priority(),
                namesEnvironment ? request.environment() : project.getBugTemplateEnvironment(),
                request.testResultId(), request.testRunId(), request.assigneeId(), request.environmentId(),
                request.exploratorySessionId(), request.customFields());
    }

    private static String orTemplate(String value, String template) {
        return isBlank(value) ? template : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** The fields the audit trail compares before and after a change (PRD-046). */
    static FieldChanges.Snapshot snapshot(BugReport bug) {
        return new FieldChanges.Snapshot()
                .with("title", bug.getTitle())
                .with("description", bug.getDescription())
                .with("stepsToReproduce", bug.getStepsToReproduce())
                .with("expectedBehavior", bug.getExpectedBehavior())
                .with("actualBehavior", bug.getActualBehavior())
                .with("priority", bug.getPriority())
                .with("status", bug.getStatus())
                .with("resolution", bug.getResolution())
                .with("duplicateOf", bug.getDuplicateOf() == null ? null : bug.getDuplicateOf().getKey())
                .with("assignee", bug.getAssignee())
                .with("environment", bug.getEnvironment());
    }

    /** How activity and notifications name a bug: its key, then its title. */
    static String label(BugReport bugReport) {
        return bugReport.getKey() + " " + bugReport.getTitle();
    }

    /** A UUID or a key; either way only within the project, so a foreign bug is a 404. */
    BugReport require(UUID projectId, String idOrKey) {
        Optional<BugReport> found = parseUuid(idOrKey)
                .map(id -> bugReportRepository.findByIdAndProjectIdWithDetails(id, projectId))
                .orElseGet(() -> bugReportRepository.findByKeyAndProjectId(idOrKey, projectId));
        return found.orElseThrow(() -> new ResourceNotFoundException("BugReport", idOrKey));
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    /*
     * PRD-027 §3.5. These three resolved through bare findById/.orElse(null), which was two bugs at
     * once.
     *
     * A bug report in project A could be linked to project B's test result and run, and
     * BugReportResponse carries testCaseTitle and testRunName — so a caller with access to A alone
     * read the names of B's cases and runs back out of its own bug list. That is the same shape as
     * the findAllById hole PRD-025 §8 fixed in TestSuiteService, and it was reachable through the
     * REST API, not just the MCP tools that prompted the audit.
     *
     * And .orElse(null) meant a mistyped id produced a bug report with no link and a 200. The
     * caller asked for a link to a specific failure and got a detached report, silently.
     */

    private TestResult requireTestResult(UUID projectId, UUID testResultId) {
        return testResultRepository.findByIdAndProjectId(testResultId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("TestResult", testResultId));
    }

    /**
     * The run a bug belongs to. A test result lives in exactly one run, so when the bug is attached
     * to a result that run is the answer whether or not the caller names it.
     *
     * <p>It used to be stored only if the caller sent it. The SPA sends both ids; an agent told to
     * "pass the resultId so the bug is reachable from its failure" sends one, and every bug it
     * filed showed no run. Deriving it here fixes every caller at once rather than asking each to
     * repeat something the server already knows. A run that contradicts the result is refused:
     * storing both would leave a bug pointing at two different runs depending on which link is read.
     */
    private TestRun resolveTestRun(UUID projectId, TestResult testResult, UUID requestedRunId) {
        if (testResult == null) {
            return requestedRunId == null ? null : requireTestRun(projectId, requestedRunId);
        }
        TestRun resultsRun = testResult.getTestRun();
        if (requestedRunId != null && !requestedRunId.equals(resultsRun.getId())) {
            throw new IllegalArgumentException("Test result " + testResult.getId() + " belongs to run "
                    + resultsRun.getKey() + ", not to run " + requestedRunId
                    + ". Omit testRunId and the run is taken from the result.");
        }
        return resultsRun;
    }

    private TestRun requireTestRun(UUID projectId, UUID testRunId) {
        return testRunRepository.findByIdAndProjectId(testRunId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", testRunId));
    }

    /** A non-member is reported missing rather than forbidden — see {@code TestRunService}. */
    User requireProjectMember(UUID projectId, UUID userId) {
        if (!projectMemberRepository.existsByUserIdAndProjectId(userId, projectId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        return userService.findEntityById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    void requireBugReportsEnabled(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        if (!project.isBugReportsEnabled()) {
            throw new ForbiddenException("Bug reports are not enabled for this project");
        }
    }

    BugReportResponse toResponseWithReporter(BugReport bugReport) {
        return toResponse(bugReport, reporterNames(List.of(bugReport)));
    }

    private List<BugReportResponse> toResponsesWithReporters(List<BugReport> bugReports) {
        Map<UUID, String> names = reporterNames(bugReports);
        return bugReports.stream()
                .map(bug -> toResponse(bug, names))
                .toList();
    }

    private BugReportResponse toResponse(BugReport bugReport, Map<UUID, String> userNames) {
        return bugReportMapper.toResponse(bugReport, nameOf(userNames, bugReport.getCreatedBy()),
                nameOf(userNames, bugReport.getUpdatedBy()));
    }

    private static String nameOf(Map<UUID, String> userNames, UUID userId) {
        return userId == null ? null : userNames.get(userId);
    }

    /** Display names of everyone who created or last updated one of these bugs. */
    private Map<UUID, String> reporterNames(List<BugReport> bugReports) {
        Set<UUID> ids = bugReports.stream()
                .flatMap(bug -> Stream.of(bug.getCreatedBy(), bug.getUpdatedBy()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : userService.findDisplayNamesByIds(ids);
    }
}
