package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.project.AddProjectMemberRequest;
import com.deanmanagement.testmanagement.project.internal.dto.project.ProjectMemberResponse;
import com.deanmanagement.testmanagement.project.internal.dto.project.UpdateProjectMemberRequest;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.service.ProjectMemberService;
import com.deanmanagement.testmanagement.shared.exception.ForbiddenException;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.UserService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
@Tag(name = "Project Members", description = "Project member management endpoints")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserService userService;

    @GetMapping
    public List<ProjectMemberResponse> findByProject(@PathVariable UUID projectId) {
        return projectMemberService.findByProject(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectMemberResponse addMember(@PathVariable UUID projectId,
                                           @Valid @RequestBody AddProjectMemberRequest request,
                                           Authentication authentication) {
        requireProjectAdmin(authentication, projectId);
        return projectMemberService.addMember(projectId, request);
    }

    @PutMapping("/{memberId}")
    public ProjectMemberResponse updateRole(@PathVariable UUID projectId,
                                            @PathVariable UUID memberId,
                                            @Valid @RequestBody UpdateProjectMemberRequest request,
                                            Authentication authentication) {
        requireProjectAdmin(authentication, projectId);
        return projectMemberService.updateRole(projectId, memberId, request);
    }

    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID projectId,
                             @PathVariable UUID memberId,
                             Authentication authentication) {
        requireProjectAdmin(authentication, projectId);
        projectMemberService.removeMember(projectId, memberId);
    }

    private void requireProjectAdmin(Authentication authentication, UUID projectId) {
        UUID userId = UUID.fromString(authentication.getName());
        boolean isProjectAdmin = projectMemberRepository.findByUserIdAndProjectId(userId, projectId)
                .map(member -> member.getRole() == ProjectRole.ADMIN)
                .orElse(false);
        if (isProjectAdmin) {
            return;
        }
        boolean isSystemAdmin = userService.findEntityById(userId)
                .map(User::isSystemAdmin)
                .orElse(false);
        if (!isSystemAdmin) {
            throw new ForbiddenException("Only project admins can manage project members");
        }
    }
}
