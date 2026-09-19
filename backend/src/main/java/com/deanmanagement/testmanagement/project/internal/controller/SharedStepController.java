package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SaveSharedStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SharedStepResponse;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SharedStepSummary;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SharedStepUsage;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.SharedStepService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Shared step blocks (PRD-030). Any member reads them; TESTER edits them, as for test cases. Per-case
 * edit grants do not extend here: one block edit changes every case that uses it.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/shared-steps")
@Tag(name = "Shared Steps", description = "Reusable blocks of steps referenced from test cases")
@RequiredArgsConstructor
public class SharedStepController {

    private final SharedStepService sharedStepService;

    @GetMapping
    @RequireProjectRole
    public Page<SharedStepSummary> list(@PathVariable UUID projectId,
                                        @RequestParam(required = false) String q,
                                        @PageableDefault(size = 50, sort = "title", direction = Sort.Direction.ASC)
                                        Pageable pageable) {
        return sharedStepService.list(projectId, q, pageable);
    }

    @GetMapping("/{id}")
    @RequireProjectRole
    public SharedStepResponse get(@PathVariable UUID projectId, @PathVariable UUID id) {
        return sharedStepService.get(projectId, id);
    }

    @GetMapping("/{id}/usages")
    @RequireProjectRole
    public List<SharedStepUsage> usages(@PathVariable UUID projectId, @PathVariable UUID id) {
        return sharedStepService.usages(projectId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireProjectRole(ProjectRole.TESTER)
    public SharedStepResponse create(@PathVariable UUID projectId, @Valid @RequestBody SaveSharedStepRequest request,
                                     Authentication authentication) {
        return sharedStepService.create(projectId, request, actor(authentication));
    }

    @PutMapping("/{id}")
    @RequireProjectRole(ProjectRole.TESTER)
    public SharedStepResponse update(@PathVariable UUID projectId, @PathVariable UUID id,
                                     @Valid @RequestBody SaveSharedStepRequest request,
                                     Authentication authentication) {
        return sharedStepService.update(projectId, id, request, actor(authentication));
    }

    /** 409 while any test case references the block. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireProjectRole(ProjectRole.TESTER)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID id, Authentication authentication) {
        sharedStepService.delete(projectId, id, actor(authentication));
    }

    private static UUID actor(Authentication authentication) {
        return authentication != null ? UUID.fromString(authentication.getName()) : null;
    }
}
