package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.bugstatus.BugStatusResponse;
import com.deanmanagement.testmanagement.project.internal.dto.bugstatus.BugStatusTransitionsRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugstatus.CreateBugStatusRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugstatus.UpdateBugStatusRequest;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.BugStatusService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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

@RestController
@RequestMapping("/api/projects/{projectId}/bug-statuses")
@Tag(name = "Bug statuses", description = "Per-project bug statuses and transitions on top of the fixed categories")
@RequiredArgsConstructor
public class ProjectBugStatusController {

    private final BugStatusService bugStatusService;

    @GetMapping
    @RequireProjectRole
    public List<BugStatusResponse> list(@PathVariable UUID projectId,
                                        @RequestParam(defaultValue = "false") boolean includeArchived) {
        return bugStatusService.list(projectId, includeArchived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireProjectRole(ProjectRole.ADMIN)
    public BugStatusResponse create(@PathVariable UUID projectId, @Valid @RequestBody CreateBugStatusRequest request,
                                    Authentication authentication) {
        return bugStatusService.create(projectId, request, actor(authentication));
    }

    @PutMapping("/{id}")
    @RequireProjectRole(ProjectRole.ADMIN)
    public BugStatusResponse update(@PathVariable UUID projectId, @PathVariable UUID id,
                                    @Valid @RequestBody UpdateBugStatusRequest request, Authentication authentication) {
        return bugStatusService.update(projectId, id, request, actor(authentication));
    }

    @GetMapping("/transitions")
    @RequireProjectRole
    public List<BugStatusTransitionsRequest.Transition> transitions(@PathVariable UUID projectId) {
        return bugStatusService.transitions(projectId);
    }

    /** Replaces the whole table; an empty list allows any move again. */
    @PutMapping("/transitions")
    @RequireProjectRole(ProjectRole.ADMIN)
    public List<BugStatusTransitionsRequest.Transition> saveTransitions(
            @PathVariable UUID projectId, @Valid @RequestBody BugStatusTransitionsRequest request,
            Authentication authentication) {
        return bugStatusService.saveTransitions(projectId, request, actor(authentication));
    }

    private static UUID actor(Authentication authentication) {
        return authentication != null ? UUID.fromString(authentication.getName()) : null;
    }
}
