package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BulkUpdateBugReportsRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.ChangeBugStatusRequest;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Triage on many bugs at once (PRD-045 §3.2). Every id and the assignee are checked before anything
 * is written, and everything happens in one transaction: all or nothing. A status change runs the
 * same rules as a single one and writes one audit entry per bug.
 */
@Service
@RequiredArgsConstructor
public class BugReportBulkService {

    private final BugReportService bugReportService;
    private final BugReportRepository bugReportRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final AuditService auditService;

    @Transactional
    public int update(UUID projectId, BulkUpdateBugReportsRequest request, UUID userId) {
        bugReportService.requireBugReportsEnabled(projectId);
        requireSomeChange(request);
        List<BugReport> bugs = requireAll(projectId, request.ids());
        User assignee = resolveAssignee(projectId, request);
        ChangeBugStatusRequest statusChange = request.status() == null ? null
                : new ChangeBugStatusRequest(request.status(), requireReason(request.reason()),
                        request.resolution(), request.duplicateOfId());

        for (BugReport bug : bugs) {
            if (statusChange != null) {
                bugReportService.applyStatus(projectId, bug, statusChange, userId);
            }
            if (assignee != null || Boolean.TRUE.equals(request.clearAssignee())) {
                bug.setAssignee(assignee);
            }
            if (request.priority() != null) {
                bug.setPriority(request.priority());
            }
            if (assignee != null || Boolean.TRUE.equals(request.clearAssignee()) || request.priority() != null) {
                auditService.log(projectId, userId, AuditAction.UPDATED, AuditEntityType.BUG_REPORT, bug.getId(),
                        BugReportService.label(bug), describe(request, assignee));
            }
        }
        bugReportRepository.saveAll(bugs);
        return bugs.size();
    }

    @Transactional
    public int delete(UUID projectId, List<UUID> ids, UUID userId) {
        bugReportService.requireBugReportsEnabled(projectId);
        List<BugReport> bugs = requireAll(projectId, ids);
        bugs.forEach(bug -> bugReportService.delete(projectId, bug, userId));
        return bugs.size();
    }

    /** Ids from another project are reported as missing, like any unknown id. */
    private List<BugReport> requireAll(UUID projectId, List<UUID> ids) {
        Set<UUID> wanted = new LinkedHashSet<>(ids);
        List<BugReport> found = bugReportRepository.findByIdInAndProjectId(wanted, projectId);
        Set<UUID> foundIds = found.stream().map(BugReport::getId).collect(Collectors.toSet());
        List<UUID> missing = wanted.stream().filter(id -> !foundIds.contains(id)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("No bug report in this project with id(s) " + missing
                    + "; nothing was changed");
        }
        return found;
    }

    private User resolveAssignee(UUID projectId, BulkUpdateBugReportsRequest request) {
        if (request.assigneeId() == null) {
            return null;
        }
        if (Boolean.TRUE.equals(request.clearAssignee())) {
            throw new IllegalArgumentException("Give assigneeId or clearAssignee, not both");
        }
        if (!projectMemberRepository.existsByUserIdAndProjectId(request.assigneeId(), projectId)) {
            throw new IllegalArgumentException("User " + request.assigneeId()
                    + " is not a member of this project; nothing was changed");
        }
        return bugReportService.requireProjectMember(projectId, request.assigneeId());
    }

    private static void requireSomeChange(BulkUpdateBugReportsRequest request) {
        boolean changes = request.assigneeId() != null || Boolean.TRUE.equals(request.clearAssignee())
                || request.priority() != null || request.status() != null;
        if (!changes) {
            throw new IllegalArgumentException("Nothing to change: give an assignee, a priority or a status");
        }
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A status change needs a reason");
        }
        return reason;
    }

    private static String describe(BulkUpdateBugReportsRequest request, User assignee) {
        StringBuilder details = new StringBuilder("Bulk change:");
        if (assignee != null) {
            details.append(" assigned to ").append(assignee.getDisplayName()).append(';');
        } else if (Boolean.TRUE.equals(request.clearAssignee())) {
            details.append(" unassigned;");
        }
        if (request.priority() != null) {
            details.append(" priority ").append(request.priority()).append(';');
        }
        return details.substring(0, details.length() - 1);
    }
}
