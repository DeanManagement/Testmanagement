package com.deanmanagement.testmanagement.project.internal.controller;
import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;

import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportResponse;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.ChangeBugStatusRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.CreateBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.UpdateBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportFilter;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BulkDeleteBugReportsRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BulkUpdateBugReportsRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.BulkOperationResponse;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.service.BugReportBulkService;
import com.deanmanagement.testmanagement.project.internal.service.BugReportLinkService;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.LinkBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.service.BugReportService;
import com.deanmanagement.testmanagement.shared.PageableUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/bug-reports")
@Tag(name = "Bug Reports", description = "Bug report management endpoints")
@RequiredArgsConstructor
public class BugReportController {

    private static final String UNASSIGNED = "none";
    private static final String ME = "me";

    private final BugReportService bugReportService;
    private final BugReportBulkService bulkService;
    private final BugReportLinkService linkService;

    /**
     * The bug list (PRD-045 §3.2). {@code assignee} takes user ids, {@code none} for unassigned and
     * {@code me}; a bug matching any of them is included. Sortable by key, title, priority, status,
     * assignee, createdAt and updatedAt.
     */
    @GetMapping
    @RequireProjectRole
    public Page<BugReportResponse> findAll(@PathVariable UUID projectId,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(required = false) List<BugReportStatus> status,
                                           @RequestParam(required = false) List<Priority> priority,
                                           @RequestParam(required = false) List<String> assignee,
                                           @RequestParam(required = false) UUID testResultId,
                                           @RequestParam(required = false) UUID environmentId,
                                           @PageableDefault(size = PageableUtils.DEFAULT_SIZE) Pageable pageable,
                                           Authentication authentication) {
        List<String> assignees = assignee == null ? List.of() : assignee;
        List<UUID> assigneeIds = assignees.stream()
                .filter(a -> !UNASSIGNED.equals(a))
                .map(a -> ME.equals(a) ? actor(authentication) : parseUserId(a))
                .toList();
        BugReportFilter filter = new BugReportFilter(q, status, priority, assigneeIds,
                assignees.contains(UNASSIGNED), testResultId, environmentId);
        return bugReportService.search(projectId, filter, pageable);
    }

    @GetMapping("/{idOrKey}")
    @RequireProjectRole
    public BugReportResponse findById(@PathVariable UUID projectId, @PathVariable String idOrKey) {
        return bugReportService.findById(projectId, idOrKey);
    }

    @PatchMapping("/bulk")
    @RequireProjectRole(ProjectRole.TESTER)
    public BulkOperationResponse bulkUpdate(@PathVariable UUID projectId,
                                            @Valid @RequestBody BulkUpdateBugReportsRequest request,
                                            Authentication authentication) {
        return affected(bulkService.update(projectId, request, actor(authentication)), "updated");
    }

    /** PRD-047: the bug showed up again in this result (and step); idempotent. */
    @PostMapping("/{idOrKey}/links")
    @RequireProjectRole(ProjectRole.TESTER)
    public BugReportResponse link(@PathVariable UUID projectId, @PathVariable String idOrKey,
                                  @Valid @RequestBody LinkBugReportRequest request, Authentication authentication) {
        return linkService.link(projectId, idOrKey, request, actor(authentication));
    }

    @DeleteMapping("/{idOrKey}/links/{linkId}")
    @RequireProjectRole(ProjectRole.TESTER)
    public BugReportResponse unlink(@PathVariable UUID projectId, @PathVariable String idOrKey,
                                    @PathVariable UUID linkId, Authentication authentication) {
        return linkService.unlink(projectId, idOrKey, linkId, actor(authentication));
    }

    @PostMapping("/bulk-delete")
    @RequireProjectRole(ProjectRole.TESTER)
    public BulkOperationResponse bulkDelete(@PathVariable UUID projectId,
                                            @Valid @RequestBody BulkDeleteBugReportsRequest request,
                                            Authentication authentication) {
        return affected(bulkService.delete(projectId, request.ids(), actor(authentication)), "deleted");
    }

    private static BulkOperationResponse affected(int count, String verb) {
        return new BulkOperationResponse(count, count + " bug report(s) " + verb);
    }

    private static UUID actor(Authentication authentication) {
        return authentication != null ? UUID.fromString(authentication.getName()) : null;
    }

    private static UUID parseUserId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("assignee must be a user id, 'none' or 'me', not '" + value + "'");
        }
    }

    @PostMapping
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.CREATED)
    public BugReportResponse create(@PathVariable UUID projectId,
                                    @Valid @RequestBody CreateBugReportRequest request,
                                    Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return bugReportService.create(projectId, request, userId);
    }

    @PutMapping("/{id}")
    @RequireProjectRole(ProjectRole.TESTER)
    public BugReportResponse update(@PathVariable UUID projectId,
                                    @PathVariable UUID id,
                                    @Valid @RequestBody UpdateBugReportRequest request,
                                    Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return bugReportService.update(projectId, id, request, userId);
    }

    @PatchMapping("/{id}/status")
    @RequireProjectRole(ProjectRole.TESTER)
    public BugReportResponse changeStatus(@PathVariable UUID projectId,
                                          @PathVariable UUID id,
                                          @Valid @RequestBody ChangeBugStatusRequest request,
                                          Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return bugReportService.changeStatus(projectId, id, request, userId);
    }

    @DeleteMapping("/{id}")
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID id,
                       Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        bugReportService.delete(projectId, id, userId);
    }
}
