package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportMapper;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportResponse;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.ChangeBugStatusRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.CreateBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.UpdateBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
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
import com.deanmanagement.testmanagement.shared.exception.ForbiddenException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    public List<BugReportResponse> findByProject(UUID projectId) {
        requireBugReportsEnabled(projectId);
        return toResponsesWithReporters(bugReportRepository.findByProjectIdWithDetails(projectId));
    }

    public BugReportResponse findById(UUID projectId, UUID id) {
        requireBugReportsEnabled(projectId);
        BugReport bugReport = bugReportRepository.findByIdAndProjectIdWithDetails(id, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("BugReport", id));
        return toResponseWithReporter(bugReport);
    }

    public List<BugReportResponse> findByTestResult(UUID projectId, UUID testResultId) {
        requireBugReportsEnabled(projectId);
        return toResponsesWithReporters(bugReportRepository.findByTestResultIdAndProjectId(testResultId, projectId));
    }

    @Transactional
    public BugReportResponse create(UUID projectId, CreateBugReportRequest request, UUID userId) {
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
        bugReport.setStatus(BugReportStatus.OPEN);
        bugReport.setEnvironment(request.environment());
        bugReport.setProject(project);

        if (request.testResultId() != null) {
            bugReport.setTestResult(requireTestResult(projectId, request.testResultId()));
        }
        bugReport.setTestRun(resolveTestRun(projectId, bugReport.getTestResult(), request.testRunId()));
        if (request.assigneeId() != null) {
            bugReport.setAssignee(requireProjectMember(projectId, request.assigneeId()));
        }

        bugReport = bugReportRepository.save(bugReport);
        auditService.log(projectId, userId, AuditAction.CREATED,
                AuditEntityType.BUG_REPORT, bugReport.getId(), bugReport.getTitle(), null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bugReportId", bugReport.getId().toString());
        data.put("title", bugReport.getTitle());
        data.put("priority", bugReport.getPriority() != null ? bugReport.getPriority().name() : null);
        data.put("status", bugReport.getStatus().name());
        eventPublisher.publishEvent(new WebhookEvent(WebhookEventType.BUG_REPORT_CREATED, projectId, data));

        return toResponseWithReporter(bugReport);
    }

    @Transactional
    public BugReportResponse update(UUID projectId, UUID id, UpdateBugReportRequest request, UUID userId) {
        requireBugReportsEnabled(projectId);
        BugReport bugReport = bugReportRepository.findByIdAndProjectIdWithDetails(id, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("BugReport", id));

        bugReport.setTitle(request.title());
        bugReport.setDescription(request.description());
        bugReport.setStepsToReproduce(request.stepsToReproduce());
        bugReport.setExpectedBehavior(request.expectedBehavior());
        bugReport.setActualBehavior(request.actualBehavior());
        bugReport.setPriority(request.priority());
        bugReport.setStatus(request.status());
        bugReport.setEnvironment(request.environment());

        // Null means "clear the link" here, deliberately — the SPA sends the whole object, so an
        // absent assignee is how a human unassigns. A supplied id that does not resolve is a
        // different thing entirely and now fails instead of silently clearing (PRD-027 §3.5).
        bugReport.setTestResult(request.testResultId() == null
                ? null : requireTestResult(projectId, request.testResultId()));
        bugReport.setTestRun(resolveTestRun(projectId, bugReport.getTestResult(), request.testRunId()));
        bugReport.setAssignee(request.assigneeId() == null
                ? null : requireProjectMember(projectId, request.assigneeId()));

        bugReport = bugReportRepository.save(bugReport);
        auditService.log(projectId, userId, AuditAction.UPDATED,
                AuditEntityType.BUG_REPORT, bugReport.getId(), bugReport.getTitle(), null);
        return toResponseWithReporter(bugReport);
    }

    @Transactional
    public BugReportResponse changeStatus(UUID projectId, UUID id, ChangeBugStatusRequest request, UUID userId) {
        requireBugReportsEnabled(projectId);
        BugReport bugReport = bugReportRepository.findByIdAndProjectIdWithDetails(id, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("BugReport", id));

        BugReportStatus oldStatus = bugReport.getStatus();
        bugReport.setStatus(request.status());
        bugReport = bugReportRepository.save(bugReport);

        String details = oldStatus + " -> " + request.status() + ": " + request.reason();
        auditService.log(projectId, userId, AuditAction.STATUS_CHANGED,
                AuditEntityType.BUG_REPORT, bugReport.getId(), bugReport.getTitle(), details);
        return toResponseWithReporter(bugReport);
    }

    public List<BugReportResponse> findByAssignee(UUID assigneeId) {
        return toResponsesWithReporters(bugReportRepository.findByAssigneeIdWithDetails(assigneeId));
    }

    @Transactional
    public void delete(UUID projectId, UUID id, UUID userId) {
        requireBugReportsEnabled(projectId);
        BugReport bugReport = bugReportRepository.findByIdAndProjectIdWithDetails(id, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("BugReport", id));
        auditService.log(projectId, userId, AuditAction.DELETED,
                AuditEntityType.BUG_REPORT, bugReport.getId(), bugReport.getTitle(), null);
        bugReportRepository.delete(bugReport);
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
    private User requireProjectMember(UUID projectId, UUID userId) {
        if (!projectMemberRepository.existsByUserIdAndProjectId(userId, projectId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        return userService.findEntityById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private void requireBugReportsEnabled(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        if (!project.isBugReportsEnabled()) {
            throw new ForbiddenException("Bug reports are not enabled for this project");
        }
    }

    private BugReportResponse toResponseWithReporter(BugReport bugReport) {
        Map<UUID, String> reporterNames = bugReport.getCreatedBy() != null
                ? userService.findDisplayNamesByIds(Set.of(bugReport.getCreatedBy()))
                : Map.of();
        return toResponseWithReporter(bugReport, reporterNames);
    }

    private List<BugReportResponse> toResponsesWithReporters(List<BugReport> bugReports) {
        Map<UUID, String> reporterNames = userService.findDisplayNamesByIds(bugReports.stream()
                .map(BugReport::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return bugReports.stream()
                .map(bugReport -> toResponseWithReporter(bugReport, reporterNames))
                .toList();
    }

    private BugReportResponse toResponseWithReporter(BugReport bugReport, Map<UUID, String> reporterNames) {
        BugReportResponse response = bugReportMapper.toResponse(bugReport);
        String reporterName = bugReport.getCreatedBy() != null
                ? reporterNames.get(bugReport.getCreatedBy())
                : null;
        return new BugReportResponse(
                response.id(),
                response.title(),
                response.description(),
                response.stepsToReproduce(),
                response.expectedBehavior(),
                response.actualBehavior(),
                response.priority(),
                response.status(),
                response.environment(),
                response.projectId(),
                response.testResultId(),
                response.testCaseTitle(),
                response.testRunId(),
                response.testRunName(),
                response.assigneeId(),
                response.assigneeName(),
                response.createdBy(),
                reporterName,
                response.createdAt(),
                response.updatedAt(),
                response.projectKey()
        );
    }
}
