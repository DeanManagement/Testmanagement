package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.project.AddProjectMemberRequest;
import com.deanmanagement.testmanagement.project.internal.dto.project.ProjectMemberResponse;
import com.deanmanagement.testmanagement.project.internal.dto.project.UpdateProjectMemberRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.shared.exception.DuplicateKeyException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final UserService userService;

    /**
     * Human members only. The service accounts behind API keys (PRD-025 §3.2) hold real memberships
     * so authorization works, but they are managed from the API-key settings page — surfacing them
     * here would put them in assignee pickers and let an admin edit a key's role in a place that
     * does not know it is editing a key.
     */
    public List<ProjectMemberResponse> findByProject(UUID projectId) {
        return projectMemberRepository.findByProjectId(projectId).stream()
                .filter(member -> !member.getUser().isServiceAccount())
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProjectMemberResponse addMember(UUID projectId, AddProjectMemberRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        User user = userService.findEntityById(request.userId())
                .filter(candidate -> !candidate.isServiceAccount())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));

        if (projectMemberRepository.existsByUserIdAndProjectId(request.userId(), projectId)) {
            throw new DuplicateKeyException(user.getEmail(), "member");
        }

        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);
        member.setRole(request.role());
        member = projectMemberRepository.save(member);
        return toResponse(member);
    }

    @Transactional
    public ProjectMemberResponse updateRole(UUID projectId, UUID memberId, UpdateProjectMemberRequest request) {
        ProjectMember member = projectMemberRepository.findById(memberId)
                .filter(candidate -> !candidate.getUser().isServiceAccount())
                .orElseThrow(() -> new ResourceNotFoundException("ProjectMember", memberId));

        member.setRole(request.role());
        member = projectMemberRepository.save(member);
        return toResponse(member);
    }

    @Transactional
    public void removeMember(UUID projectId, UUID memberId) {
        ProjectMember member = projectMemberRepository.findById(memberId)
                .filter(candidate -> !candidate.getUser().isServiceAccount())
                .orElseThrow(() -> new ResourceNotFoundException("ProjectMember", memberId));
        projectMemberRepository.delete(member);
    }

    private ProjectMemberResponse toResponse(ProjectMember member) {
        return new ProjectMemberResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getUser().getDisplayName(),
                member.getRole(),
                member.getCreatedAt()
        );
    }
}
