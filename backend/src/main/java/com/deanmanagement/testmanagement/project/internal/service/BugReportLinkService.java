package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportResponse;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.LinkBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportLink;
import com.deanmanagement.testmanagement.project.internal.entity.StepResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportLinkRepository;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestPlanRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Where a bug showed up besides where it was found (PRD-047). The found-in result stays on the bug;
 * each later occurrence is a link, so a regression in run 19 doesn't erase that the bug came from
 * run 17. Every id is resolved within the project: a foreign one is a 404.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BugReportLinkService {

    private final BugReportService bugReportService;
    private final BugReportRepository bugReportRepository;
    private final BugReportLinkRepository linkRepository;
    private final TestPlanRepository testPlanRepository;
    private final AuditService auditService;

    /**
     * Idempotent: linking the found-in result again, or a result already linked, changes nothing
     * except a step named for the first time.
     */
    @Transactional
    public BugReportResponse link(UUID projectId, String bugIdOrKey, LinkBugReportRequest request, UUID userId) {
        bugReportService.requireBugReportsEnabled(projectId);
        BugReport bug = bugReportService.require(projectId, bugIdOrKey);
        TestResult result = bugReportService.requireTestResult(projectId, request.testResultId());
        StepResult step = request.stepResultId() == null ? null : stepOf(result, request.stepResultId());

        if (isFoundIn(bug, result)) {
            return bugReportService.toResponseWithReporter(bug);
        }
        BugReportLink link = linkRepository.findByBugReportIdAndTestResultId(bug.getId(), result.getId())
                .orElseGet(() -> newLink(bug, result));
        boolean isNew = link.getId() == null;
        if (step != null) {
            link.setStepResult(step);
        }
        linkRepository.save(link);
        if (isNew) {
            auditService.log(projectId, userId, AuditAction.UPDATED, AuditEntityType.BUG_REPORT, bug.getId(),
                    BugReportService.label(bug), "Linked to " + where(result, step));
        }
        return bugReportService.toResponseWithReporter(bug);
    }

    @Transactional
    public BugReportResponse unlink(UUID projectId, String bugIdOrKey, UUID linkId, UUID userId) {
        bugReportService.requireBugReportsEnabled(projectId);
        BugReport bug = bugReportService.require(projectId, bugIdOrKey);
        BugReportLink link = linkRepository.findByIdAndBugReportId(linkId, bug.getId())
                .orElseThrow(() -> new ResourceNotFoundException("BugReportLink", linkId));
        bug.getLinks().remove(link);
        auditService.log(projectId, userId, AuditAction.UPDATED, AuditEntityType.BUG_REPORT, bug.getId(),
                BugReportService.label(bug), "Unlinked from " + where(link.getTestResult(), link.getStepResult()));
        return bugReportService.toResponseWithReporter(bugReportRepository.save(bug));
    }

    /** Bugs found in a run of the plan, or linked to a result of one (PRD-047). */
    public List<BugReportResponse> findByTestPlan(UUID projectId, UUID planId) {
        bugReportService.requireBugReportsEnabled(projectId);
        testPlanRepository.findByIdAndProjectId(planId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("TestPlan", planId));
        return bugReportService.toResponsesWithReporters(bugReportRepository.findByTestPlan(projectId, planId));
    }

    /** For callers that number steps as the execution view does (MCP): step N of the result, from 1. */
    public UUID stepResultId(UUID projectId, UUID testResultId, int stepNumber) {
        if (testResultId == null) {
            throw new IllegalArgumentException("stepNumber needs the testResultId it belongs to");
        }
        TestResult result = bugReportService.requireTestResult(projectId, testResultId);
        return result.getStepResults().stream()
                .filter(step -> step.getPosition() != null && step.getPosition() == stepNumber - 1)
                .findFirst()
                .map(StepResult::getId)
                .orElseThrow(() -> new IllegalArgumentException("Test result " + testResultId + " has no step "
                        + stepNumber + "; it has " + result.getStepResults().size()));
    }

    /** A step of this result; any other id, including another result's step, is refused. */
    static StepResult stepOf(TestResult result, UUID stepResultId) {
        return result.getStepResults().stream()
                .filter(step -> step.getId().equals(stepResultId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Step result " + stepResultId + " is not a step of test result " + result.getId()));
    }

    private static boolean isFoundIn(BugReport bug, TestResult result) {
        return bug.getTestResult() != null && Objects.equals(bug.getTestResult().getId(), result.getId());
    }

    private static BugReportLink newLink(BugReport bug, TestResult result) {
        BugReportLink link = new BugReportLink();
        link.setBugReport(bug);
        link.setTestResult(result);
        bug.getLinks().add(link);
        return link;
    }

    private static String where(TestResult result, StepResult step) {
        String place = result.getTestRun().getKey() + " / " + result.getTestCase().getKey();
        return step == null || step.getPosition() == null ? place : place + ", step " + (step.getPosition() + 1);
    }
}
