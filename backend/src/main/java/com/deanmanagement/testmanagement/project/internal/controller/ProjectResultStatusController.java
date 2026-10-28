package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.resultstatus.CreateResultStatusRequest;
import com.deanmanagement.testmanagement.project.internal.dto.resultstatus.ResultStatusResponse;
import com.deanmanagement.testmanagement.project.internal.dto.resultstatus.UpdateResultStatusRequest;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.ProjectResultStatusService;
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

/** A project's result statuses (TM24). Any member can read them; defining them is ADMIN. */
@RestController
@RequestMapping("/api/projects/{projectId}/result-statuses")
@Tag(name = "Result statuses", description = "Per-project result statuses on top of the fixed categories")
@RequiredArgsConstructor
public class ProjectResultStatusController {

    private final ProjectResultStatusService resultStatusService;

    @GetMapping
    @RequireProjectRole
    public List<ResultStatusResponse> list(@PathVariable UUID projectId,
                                           @RequestParam(defaultValue = "false") boolean includeArchived) {
        return resultStatusService.list(projectId, includeArchived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireProjectRole(ProjectRole.ADMIN)
    public ResultStatusResponse create(@PathVariable UUID projectId,
                                       @Valid @RequestBody CreateResultStatusRequest request,
                                       Authentication authentication) {
        return resultStatusService.create(projectId, request, actor(authentication));
    }

    @PutMapping("/{id}")
    @RequireProjectRole(ProjectRole.ADMIN)
    public ResultStatusResponse update(@PathVariable UUID projectId, @PathVariable UUID id,
                                       @Valid @RequestBody UpdateResultStatusRequest request,
                                       Authentication authentication) {
        return resultStatusService.update(projectId, id, request, actor(authentication));
    }

    private static UUID actor(Authentication authentication) {
        return authentication != null ? UUID.fromString(authentication.getName()) : null;
    }
}
