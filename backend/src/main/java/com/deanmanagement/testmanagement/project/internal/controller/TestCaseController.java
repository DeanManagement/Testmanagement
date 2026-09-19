package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseContextResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseExecutionResponse;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseContextService;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseReviewService;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.ReviewCapabilitiesResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.RequestChangesRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.ApproveTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.BulkDeleteRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.BulkOperationResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.BulkStatusRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.filter.TestCaseListFilter;
import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.dto.environment.EnvironmentResultResponse;
import com.deanmanagement.testmanagement.project.internal.service.ProjectEnvironmentService;
import com.deanmanagement.testmanagement.project.internal.service.CustomFieldFilterParser;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseService;
import com.deanmanagement.testmanagement.shared.PageableUtils;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/test-cases")
@Tag(name = "Test Cases", description = "Test case management endpoints")
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseService testCaseService;
    private final ProjectEnvironmentService environmentService;
    private final TestCaseReviewService reviewService;
    private final CustomFieldFilterParser customFieldFilterParser;
    private final TestCaseContextService contextService;

    @GetMapping
    @RequireProjectRole
    public Page<TestCaseResponse> findAll(@PathVariable UUID projectId,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(required = false) List<TestCaseStatus> status,
                                          @RequestParam(required = false) List<Priority> priority,
                                          @Parameter(description = "Only cases carrying all of these labels")
                                          @RequestParam(required = false) List<String> label,
                                          @RequestParam(required = false) UUID folderId,
                                          @RequestParam(required = false, defaultValue = "false") boolean includeSubfolders,
                                          @RequestParam(required = false, defaultValue = "false") boolean rootOnly,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant updatedAfter,
                                          @RequestParam MultiValueMap<String, String> params,
                                          @PageableDefault(size = PageableUtils.DEFAULT_SIZE) Pageable pageable) {
        TestCaseListFilter filter =
                new TestCaseListFilter(q, status, priority, label, folderId, includeSubfolders, rootOnly,
                        updatedAfter, customFieldFilterParser.parse(projectId, CustomFieldEntityType.TEST_CASE, params));
        return testCaseService.findByProject(projectId, filter, PageableUtils.normalize(pageable));
    }

    /** The project's labels, distinct and sorted: the label filter's options (PRD-052). */
    @GetMapping("/labels")
    @RequireProjectRole
    public List<String> labels(@PathVariable UUID projectId) {
        return testCaseService.labels(projectId);
    }

    @GetMapping("/{id}")
    @RequireProjectRole
    public TestCaseResponse findById(@PathVariable UUID projectId, @PathVariable UUID id) {
        return testCaseService.findById(projectId, id);
    }

    /** What the caller may do in this case's review (PRD-033). */
    @GetMapping("/{id}/review-capabilities")
    @RequireProjectRole
    public ReviewCapabilitiesResponse reviewCapabilities(@PathVariable UUID projectId, @PathVariable UUID id,
                                                         Authentication authentication) {
        return reviewService.capabilities(projectId, id, userIdOf(authentication));
    }

    @PostMapping("/{id}/submit-review")
    @RequireProjectRole(ProjectRole.TESTER)
    public TestCaseResponse submitForReview(@PathVariable UUID projectId, @PathVariable UUID id,
                                            Authentication authentication) {
        return reviewService.submitForReview(projectId, id, userIdOf(authentication));
    }

    /** TESTER at the edge; the service applies the reviewer role and the not-the-author rule. */
    @PostMapping("/{id}/approve")
    @RequireProjectRole(ProjectRole.TESTER)
    public TestCaseResponse approve(@PathVariable UUID projectId, @PathVariable UUID id,
                                    @Valid @RequestBody ApproveTestCaseRequest request,
                                    Authentication authentication) {
        return reviewService.approve(projectId, id, request.version(), Boolean.TRUE.equals(request.force()),
                userIdOf(authentication));
    }

    @PostMapping("/{id}/request-changes")
    @RequireProjectRole(ProjectRole.TESTER)
    public TestCaseResponse requestChanges(@PathVariable UUID projectId, @PathVariable UUID id,
                                           @Valid @RequestBody(required = false) RequestChangesRequest request,
                                           Authentication authentication) {
        return reviewService.requestChanges(projectId, id, request != null ? request.comment() : null,
                userIdOf(authentication));
    }

    private static UUID userIdOf(Authentication authentication) {
        return authentication != null ? UUID.fromString(authentication.getName()) : null;
    }

    /** The case's latest executed result in each environment (PRD-032). */
    /** PRD-050: who made the case, its folder path and the suites that include it. */
    @GetMapping("/{id}/context")
    @RequireProjectRole
    public TestCaseContextResponse getContext(@PathVariable UUID projectId, @PathVariable UUID id) {
        return contextService.context(projectId, id);
    }

    /** PRD-050: everywhere the case ran or is scheduled to run, newest run first. */
    @GetMapping("/{id}/executions")
    @RequireProjectRole
    public Page<TestCaseExecutionResponse> getExecutions(@PathVariable UUID projectId, @PathVariable UUID id,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return contextService.executions(projectId, id, page, size);
    }

    @GetMapping("/{id}/results/by-environment")
    @RequireProjectRole
    public List<EnvironmentResultResponse> latestResultsByEnvironment(@PathVariable UUID projectId,
                                                                      @PathVariable UUID id) {
        return environmentService.latestResultsByEnvironment(projectId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireProjectRole(ProjectRole.TESTER)
    public TestCaseResponse create(@PathVariable UUID projectId,
                                   @Valid @RequestBody CreateTestCaseRequest request,
                                   Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return testCaseService.create(projectId, request, userId);
    }

    @PutMapping("/{id}")
    @RequireProjectRole(ProjectRole.TESTER)
    public TestCaseResponse update(@PathVariable UUID projectId,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody UpdateTestCaseRequest request,
                                   Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return testCaseService.update(projectId, id, request, userId);
    }

    /** PRD-030: replaces a shared step reference with copies of the block's steps. */
    @PostMapping("/{id}/steps/{stepId}/inline")
    @RequireProjectRole(ProjectRole.TESTER)
    public TestCaseResponse inlineSharedStep(@PathVariable UUID projectId, @PathVariable UUID id,
                                             @PathVariable UUID stepId, Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return testCaseService.inlineSharedStep(projectId, id, stepId, userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireProjectRole(ProjectRole.TESTER)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID id,
                       Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        testCaseService.delete(projectId, id, userId);
    }

    @PostMapping("/bulk-status")
    @RequireProjectRole(ProjectRole.TESTER)
    public BulkOperationResponse bulkUpdateStatus(@PathVariable UUID projectId,
                                                   @Valid @RequestBody BulkStatusRequest request,
                                                   Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return testCaseService.bulkUpdateStatus(projectId, request, userId);
    }

    @PostMapping("/bulk-delete")
    @RequireProjectRole(ProjectRole.TESTER)
    public BulkOperationResponse bulkDelete(@PathVariable UUID projectId,
                                             @Valid @RequestBody BulkDeleteRequest request,
                                             Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return testCaseService.bulkDelete(projectId, request, userId);
    }
}
