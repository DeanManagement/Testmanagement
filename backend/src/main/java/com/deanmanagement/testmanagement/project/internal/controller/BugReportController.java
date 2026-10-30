package com.deanmanagement.testmanagement.project.internal.controller;
import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;

import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportResponse;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.ChangeBugStatusRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.CreateBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.UpdateBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BulkDeleteBugReportsRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BulkUpdateBugReportsRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.BulkOperationResponse;
import com.deanmanagement.testmanagement.project.internal.service.BugReportBulkService;
import com.deanmanagement.testmanagement.project.internal.service.BugReportLinkService;
import com.deanmanagement.testmanagement.project.internal.service.BugReportRelationService;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportLinksResponse;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.LinkBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.LinkItemRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.RelateBugReportRequest;
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
import java.nio.charset.StandardCharsets;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.data.web.SortDefault;
import org.springframework.data.domain.Sort;
import com.deanmanagement.testmanagement.project.internal.service.BugReportExportService;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportListParams;

@RestController
@RequestMapping("/api/projects/{projectId}/bug-reports")
@Tag(name = "Bug Reports", description = "Bug report management endpoints")
@RequiredArgsConstructor
public class BugReportController {

    private final BugReportService bugReportService;
    private final BugReportBulkService bulkService;
    private final BugReportLinkService linkService;
    private final BugReportRelationService relationService;
    private final BugReportExportService exportService;

    /**
     * The bug list (PRD-045 §3.2), filtered as {@link BugReportListParams} describes. Sortable by
     * key, title, priority, status, assignee, createdAt and updatedAt.
     */
    @GetMapping
    @RequireProjectRole
    public Page<BugReportResponse> findAll(@PathVariable UUID projectId, BugReportListParams params,
                                           @PageableDefault(size = PageableUtils.DEFAULT_SIZE) Pageable pageable,
                                           Authentication authentication) {
        return bugReportService.search(projectId, params.toFilter(actor(authentication)), pageable);
    }

    /** TM48: the filtered list, every page, in the list's sort order. */
    @GetMapping("/export.csv")
    @RequireProjectRole
    public ResponseEntity<byte[]> exportCsv(@PathVariable UUID projectId, BugReportListParams params,
                                            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Sort sort,
                                            Authentication authentication) {
        byte[] csv = exportService.exportCsv(projectId, params.toFilter(actor(authentication)), sort);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("bug-reports.csv").build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    /** Labels already used on this project's bugs, for the form's autocomplete (TM49). */
    @GetMapping("/labels")
    @RequireProjectRole
    public List<String> labels(@PathVariable UUID projectId) {
        return bugReportService.labels(projectId);
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

    /** TM46: test cases, requirements and other bugs linked by hand. */
    @GetMapping("/{idOrKey}/relations")
    @RequireProjectRole
    public BugReportLinksResponse relations(@PathVariable UUID projectId, @PathVariable String idOrKey) {
        return relationService.links(projectId, idOrKey);
    }

    @PostMapping("/{idOrKey}/relations/test-cases")
    @RequireProjectRole(ProjectRole.TESTER)
    public BugReportLinksResponse linkTestCase(@PathVariable UUID projectId, @PathVariable String idOrKey,
                                               @Valid @RequestBody LinkItemRequest request,
                                               Authentication authentication) {
        return relationService.linkTestCase(projectId, idOrKey, request.reference(), actor(authentication));
    }

    @DeleteMapping("/{idOrKey}/relations/test-cases/{testCase}")
    @RequireProjectRole(ProjectRole.TESTER)
    public BugReportLinksResponse unlinkTestCase(@PathVariable UUID projectId, @PathVariable String idOrKey,
                                                 @PathVariable String testCase, Authentication authentication) {
        return relationService.unlinkTestCase(projectId, idOrKey, testCase, actor(authentication));
    }

    @PostMapping("/{idOrKey}/relations/requirements")
    @RequireProjectRole(ProjectRole.TESTER)
    public BugReportLinksResponse linkRequirement(@PathVariable UUID projectId, @PathVariable String idOrKey,
                                                  @Valid @RequestBody LinkItemRequest request,
                                                  Authentication authentication) {
        return relationService.linkRequirement(projectId, idOrKey, request.reference(), actor(authentication));
    }

    @DeleteMapping("/{idOrKey}/relations/requirements/{requirement}")
    @RequireProjectRole(ProjectRole.TESTER)
    public BugReportLinksResponse unlinkRequirement(@PathVariable UUID projectId, @PathVariable String idOrKey,
                                                    @PathVariable String requirement, Authentication authentication) {
        return relationService.unlinkRequirement(projectId, idOrKey, requirement, actor(authentication));
    }

    @PostMapping("/{idOrKey}/relations/bugs")
    @RequireProjectRole(ProjectRole.TESTER)
    public BugReportLinksResponse relate(@PathVariable UUID projectId, @PathVariable String idOrKey,
                                         @Valid @RequestBody RelateBugReportRequest request,
                                         Authentication authentication) {
        return relationService.relate(projectId, idOrKey, request, actor(authentication));
    }

    @DeleteMapping("/{idOrKey}/relations/bugs/{relationId}")
    @RequireProjectRole(ProjectRole.TESTER)
    public BugReportLinksResponse unrelate(@PathVariable UUID projectId, @PathVariable String idOrKey,
                                           @PathVariable UUID relationId, Authentication authentication) {
        return relationService.unrelate(projectId, idOrKey, relationId, actor(authentication));
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
