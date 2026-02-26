package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportMapper;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportResponse;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.CreateBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.UpdateBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.shared.exception.ForbiddenException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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

    public List<BugReportResponse> findByProject(UUID projectId) {
        requireBugReportsEnabled(projectId);
        return bugReportRepository.findByProjectIdWithDetails(projectId).stream()
                .map(this::toResponseWithReporter)
                .toList();
    }

    public BugReportResponse findById(UUID projectId, UUID id) {
        requireBugReportsEnabled(projectId);
        BugReport bugReport = bugReportRepository.findByIdAndProjectIdWithDetails(id, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("BugReport", id));
        return toResponseWithReporter(bugReport);
    }

    public List<BugReportResponse> findByTestResult(UUID projectId, UUID testResultId) {
        requireBugReportsEnabled(projectId);
        return bugReportRepository.findByTestResultIdAndProjectId(testResultId, projectId).stream()
                .map(this::toResponseWithReporter)
                .toList();
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
        BugReportResponse response = bugReportMapper.toResponse(bugReport);
        String reporterName = null;
        if (bugReport.getCreatedBy() != null) {
            reporterName = userService.findEntityById(bugReport.getCreatedBy())
                    .map(User::getDisplayName)
                    .orElse(null);
        }
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
                response.updatedAt()
        );
    }
}
