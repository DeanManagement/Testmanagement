package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.buildserver.BuildServerProviderRegistry;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.AssignWorkflowProjectsRequest;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.BuildServerConfigResponse;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.BuildWorkflowResponse;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.DiscoverWorkflowsRequest;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.DiscoverWorkflowsResponse;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.SaveBuildServerConfigRequest;
import com.deanmanagement.testmanagement.project.internal.dto.buildserver.SaveBuildWorkflowRequest;
import com.deanmanagement.testmanagement.project.internal.entity.BuildServerProviderType;
import com.deanmanagement.testmanagement.project.internal.service.BuildServerConfigService;
import com.deanmanagement.testmanagement.project.internal.service.BuildWorkflowService;
import com.deanmanagement.testmanagement.shared.exception.ForbiddenException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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
import java.util.Set;
import java.util.UUID;

/**
 * Instance-wide build-server administration (PRD-024 §3.4). Everything here is restricted to
 * system administrators, the same guard as user management: servers, workflow definitions and
 * project assignments are global concerns, and this controller is the only place server URLs or
 * error details are ever exposed.
 */
@RestController
@RequestMapping("/api/build-servers")
@Tag(name = "Build Servers", description = "Global build server and workflow administration")
@RequiredArgsConstructor
public class BuildServerAdminController {

    private final BuildServerConfigService configService;
    private final BuildWorkflowService workflowService;
    private final BuildServerProviderRegistry providerRegistry;

    /** Providers with a working adapter, so the UI does not offer one that cannot work. */
    @GetMapping("/providers")
    public Set<BuildServerProviderType> supportedProviders(Authentication authentication) {
        requireAdmin(authentication);
        return providerRegistry.supported();
    }

    @GetMapping
    public List<BuildServerConfigResponse> list(Authentication authentication) {
        requireAdmin(authentication);
        return configService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BuildServerConfigResponse create(@Valid @RequestBody SaveBuildServerConfigRequest request,
                                            Authentication authentication) {
        requireAdmin(authentication);
        return configService.create(request);
    }

    @PutMapping("/{id}")
    public BuildServerConfigResponse update(@PathVariable UUID id,
                                            @Valid @RequestBody SaveBuildServerConfigRequest request,
                                            Authentication authentication) {
        requireAdmin(authentication);
        return configService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication authentication) {
        requireAdmin(authentication);
        configService.delete(id);
    }

    /** Verifies the stored credentials against the server. 502 if it rejects us. */
    @PostMapping("/{id}/test")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void testConnection(@PathVariable UUID id, Authentication authentication) {
        requireAdmin(authentication);
        configService.testConnection(id);
    }

    /** Provider pick-list for the workflow form; {@code supported=false} means enter manually. */
    @PostMapping("/{id}/discover")
    public DiscoverWorkflowsResponse discover(@PathVariable UUID id,
                                              @Valid @RequestBody DiscoverWorkflowsRequest request,
                                              Authentication authentication) {
        requireAdmin(authentication);
        return configService.discover(id, request.repoRef());
    }

    @GetMapping("/{id}/workflows")
    public List<BuildWorkflowResponse> listWorkflows(@PathVariable UUID id, Authentication authentication) {
        requireAdmin(authentication);
        return workflowService.listForServer(id);
    }

    @PostMapping("/{id}/workflows")
    @ResponseStatus(HttpStatus.CREATED)
    public BuildWorkflowResponse createWorkflow(@PathVariable UUID id,
                                                @Valid @RequestBody SaveBuildWorkflowRequest request,
                                                Authentication authentication) {
        requireAdmin(authentication);
        return workflowService.create(id, request);
    }

    @PutMapping("/workflows/{workflowId}")
    public BuildWorkflowResponse updateWorkflow(@PathVariable UUID workflowId,
                                                @Valid @RequestBody SaveBuildWorkflowRequest request,
                                                Authentication authentication) {
        requireAdmin(authentication);
        return workflowService.update(workflowId, request);
    }

    @DeleteMapping("/workflows/{workflowId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorkflow(@PathVariable UUID workflowId, Authentication authentication) {
        requireAdmin(authentication);
        workflowService.delete(workflowId);
    }

    @GetMapping("/workflows/{workflowId}/projects")
    public List<UUID> assignedProjects(@PathVariable UUID workflowId, Authentication authentication) {
        requireAdmin(authentication);
        return workflowService.assignedProjects(workflowId);
    }

    /** Replaces the workflow's project assignments with exactly the submitted set. */
    @PutMapping("/workflows/{workflowId}/projects")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignProjects(@PathVariable UUID workflowId,
                               @Valid @RequestBody AssignWorkflowProjectsRequest request,
                               Authentication authentication) {
        requireAdmin(authentication);
        workflowService.assignProjects(workflowId, request.projectIds());
    }

    private void requireAdmin(Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        if (!isAdmin) {
            throw new ForbiddenException("Only system administrators can manage build servers");
        }
    }
}
