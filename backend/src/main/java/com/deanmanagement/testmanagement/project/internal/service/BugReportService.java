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
import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import com.deanmanagement.testmanagement.project.internal.webhook.WebhookEvent;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.shared.exception.ForbiddenException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
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
            bugReport.setTestResult(testResultRepository.findById(request.testResultId()).orElse(null));
        }
        if (request.testRunId() != null) {
            bugReport.setTestRun(testRunRepository.findById(request.testRunId()).orElse(null));
        }
        if (request.assigneeId() != null) {
            bugReport.setAssignee(userService.findEntityById(request.assigneeId()).orElse(null));
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

        if (request.testResultId() != null) {
            bugReport.setTestResult(testResultRepository.findById(request.testResultId()).orElse(null));
        } else {
            bugReport.setTestResult(null);
        }
        if (request.testRunId() != null) {
            bugReport.setTestRun(testRunRepository.findById(request.testRunId()).orElse(null));
        } else {
            bugReport.setTestRun(null);
        }
        if (request.assigneeId() != null) {
            bugReport.setAssignee(userService.findEntityById(request.assigneeId()).orElse(null));
        } else {
            bugReport.setAssignee(null);
        }

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
