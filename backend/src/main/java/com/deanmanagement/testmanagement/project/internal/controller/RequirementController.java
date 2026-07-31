package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.CoverageSummaryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.RequirementResponse;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.SaveRequirementRequest;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.TraceabilityRowResponse;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.RequirementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Requirements and traceability (PRD-014). Reading is open to any member; writing needs TESTER,
 * matching who may change test cases.
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
@Tag(name = "Requirements", description = "Requirements and the traceability matrix")
@RequiredArgsConstructor
public class RequirementController {

    private final RequirementService requirementService;

    @GetMapping("/requirements")
    @RequireProjectRole
    public Page<RequirementResponse> list(@PathVariable UUID projectId, Pageable pageable) {
        return requirementService.list(projectId, pageable);
    }

    @GetMapping("/requirements/{id}")
    @RequireProjectRole
    public RequirementResponse get(@PathVariable UUID projectId, @PathVariable UUID id) {
        return requirementService.get(projectId, id);
    }

    @PostMapping("/requirements")
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.CREATED)
    public RequirementResponse create(@PathVariable UUID projectId,
                                      @Valid @RequestBody SaveRequirementRequest request,
                                      Authentication authentication) {
        return requirementService.create(projectId, request, actor(authentication));
    }

    @PutMapping("/requirements/{id}")
    @RequireProjectRole(ProjectRole.TESTER)
    public RequirementResponse update(@PathVariable UUID projectId,
                                      @PathVariable UUID id,
                                      @Valid @RequestBody SaveRequirementRequest request,
                                      Authentication authentication) {
        return requirementService.update(projectId, id, request, actor(authentication));
    }

    @DeleteMapping("/requirements/{id}")
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID id,
                       Authentication authentication) {
        requirementService.delete(projectId, id, actor(authentication));
    }

    @PostMapping("/requirements/{id}/test-cases/{testCaseId}")
    @RequireProjectRole(ProjectRole.TESTER)
    public RequirementResponse link(@PathVariable UUID projectId,
                                    @PathVariable UUID id,
                                    @PathVariable UUID testCaseId,
                                    Authentication authentication) {
        return requirementService.linkTestCase(projectId, id, testCaseId, actor(authentication));
    }

    @DeleteMapping("/requirements/{id}/test-cases/{testCaseId}")
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable UUID projectId,
                       @PathVariable UUID id,
                       @PathVariable UUID testCaseId,
                       Authentication authentication) {
        requirementService.unlinkTestCase(projectId, id, testCaseId, actor(authentication));
    }

    @GetMapping("/traceability")
    @RequireProjectRole
    public List<TraceabilityRowResponse> matrix(@PathVariable UUID projectId) {
        return requirementService.matrix(projectId);
    }

    @GetMapping("/traceability/coverage")
    @RequireProjectRole
    public CoverageSummaryResponse coverage(@PathVariable UUID projectId) {
        return requirementService.coverage(projectId);
    }

    private static UUID actor(Authentication authentication) {
        return authentication != null ? UUID.fromString(authentication.getName()) : null;
    }
}
