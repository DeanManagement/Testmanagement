package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.environment.CreateEnvironmentRequest;
import com.deanmanagement.testmanagement.project.internal.dto.environment.EnvironmentResponse;
import com.deanmanagement.testmanagement.project.internal.dto.environment.MergeEnvironmentRequest;
import com.deanmanagement.testmanagement.project.internal.dto.environment.UpdateEnvironmentRequest;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.ProjectEnvironmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * The environment catalogue (PRD-032). Any member can read it; curating it is ADMIN. Registering a
 * new name implicitly through a run, bug or CI write needs only the role that write requires.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/environments")
@Tag(name = "Environments", description = "Per-project environment catalogue")
@RequiredArgsConstructor
public class ProjectEnvironmentController {

    private final ProjectEnvironmentService environmentService;

    @GetMapping
    @RequireProjectRole
    public List<EnvironmentResponse> list(@PathVariable UUID projectId,
                                          @RequestParam(defaultValue = "false") boolean includeArchived) {
        return environmentService.list(projectId, includeArchived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireProjectRole(ProjectRole.ADMIN)
    public EnvironmentResponse create(@PathVariable UUID projectId,
                                      @Valid @RequestBody CreateEnvironmentRequest request,
                                      Authentication authentication) {
        return environmentService.create(projectId, request, actor(authentication));
    }

    @PutMapping("/{id}")
    @RequireProjectRole(ProjectRole.ADMIN)
    public EnvironmentResponse update(@PathVariable UUID projectId, @PathVariable UUID id,
                                      @Valid @RequestBody UpdateEnvironmentRequest request,
                                      Authentication authentication) {
        return environmentService.update(projectId, id, request, actor(authentication));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireProjectRole(ProjectRole.ADMIN)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID id, Authentication authentication) {
        environmentService.delete(projectId, id, actor(authentication));
    }

    @PostMapping("/{id}/merge")
    @RequireProjectRole(ProjectRole.ADMIN)
    public EnvironmentResponse merge(@PathVariable UUID projectId, @PathVariable UUID id,
                                     @Valid @RequestBody MergeEnvironmentRequest request,
                                     Authentication authentication) {
        return environmentService.merge(projectId, id, request.targetId(), actor(authentication));
    }

    private static UUID actor(Authentication authentication) {
        return authentication != null ? UUID.fromString(authentication.getName()) : null;
    }
}
