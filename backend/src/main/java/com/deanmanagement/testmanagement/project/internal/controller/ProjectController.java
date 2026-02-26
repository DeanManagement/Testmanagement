package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.dashboard.DashboardResponse;
import com.deanmanagement.testmanagement.project.internal.dto.project.CreateProjectRequest;
import com.deanmanagement.testmanagement.project.internal.dto.project.ProjectResponse;
import com.deanmanagement.testmanagement.project.internal.dto.project.UpdateProjectRequest;
import com.deanmanagement.testmanagement.project.internal.service.DashboardService;
import com.deanmanagement.testmanagement.project.internal.service.ProjectService;
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

import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Project management endpoints")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final DashboardService dashboardService;

    @GetMapping
    public List<ProjectResponse> findAll(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        boolean isSystemAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        return projectService.findAll(userId, isSystemAdmin);
    }

    @GetMapping("/search")
    public List<ProjectResponse> searchByKey(@RequestParam String key) {
        return projectService.searchByKey(key);
    }

    @GetMapping("/{id}")
    public ProjectResponse findById(@PathVariable UUID id) {
        return projectService.findById(id);
    }

    @GetMapping("/{id}/dashboard")
    public DashboardResponse getDashboard(@PathVariable UUID id) {
        return dashboardService.getDashboard(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest request,
                                  Authentication authentication) {
        requireAdmin(authentication);
        UUID userId = UUID.fromString(authentication.getName());
        return projectService.create(request, userId);
    }

    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody UpdateProjectRequest request,
                                  Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return projectService.update(id, request, userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        projectService.delete(id, userId);
    }

    private void requireAdmin(Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        if (!isAdmin) {
            throw new ForbiddenException("Only system administrators can create projects");
        }
    }
}
