package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.project.CreateProjectRequest;
import com.deanmanagement.testmanagement.project.internal.dto.project.ProjectMapper;
import com.deanmanagement.testmanagement.project.internal.dto.project.ProjectResponse;
import com.deanmanagement.testmanagement.project.internal.dto.project.UpdateProjectRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private AuditService auditService;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    /** Deleting a project now clears its executions first — see ProjectDeletionWithResultsTest. */
    @Mock
    private com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository testRunRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ProjectService projectService;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    private Project sampleProject() {
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setName("Test Project");
        project.setDescription("A test project");
        project.setKey("TEST");
        return project;
    }

    private ProjectResponse sampleResponse() {
        return new ProjectResponse(PROJECT_ID, "Test Project", "A test project", "TEST", false, NOW, NOW, null, null);
    }

    @Test
    void findAll_asAdmin_returnsAllProjects() {
        Project project = sampleProject();
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectMapper.toResponse(project)).thenReturn(sampleResponse());

        List<ProjectResponse> result = projectService.findAll(UUID.randomUUID(), true);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Test Project");
    }

    @Test
    void findAll_asNonAdmin_returnsMemberProjects() {
        UUID userId = UUID.randomUUID();
        Project project = sampleProject();
        when(projectRepository.findByMembersUserId(userId)).thenReturn(List.of(project));
        when(projectMapper.toResponse(project)).thenReturn(sampleResponse());

        List<ProjectResponse> result = projectService.findAll(userId, false);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Test Project");
    }

    @Test
    void findById_returnsProject() {
        Project project = sampleProject();
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectMapper.toResponse(project)).thenReturn(sampleResponse());

        ProjectResponse result = projectService.findById(PROJECT_ID);

        assertThat(result.key()).isEqualTo("TEST");
    }

    @Test
    void findById_notFound_throwsException() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.findById(PROJECT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_savesAndReturnsProject() {
        var request = new CreateProjectRequest("New Project", "Description");
        Project project = sampleProject();

        when(projectMapper.toEntity(request)).thenReturn(project);
        when(projectRepository.existsByKey("NP")).thenReturn(false);
        when(projectRepository.save(project)).thenReturn(project);
        when(projectMapper.toResponse(project)).thenReturn(sampleResponse());

        ProjectResponse result = projectService.create(request, null);

        assertThat(result).isNotNull();
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void create_addsCreatorAsAdminMember() {
        var request = new CreateProjectRequest("New Project", "Description");
        Project project = sampleProject();
        UUID creatorId = UUID.randomUUID();
        User creator = new User();
        creator.setId(creatorId);

        when(projectMapper.toEntity(request)).thenReturn(project);
        when(projectRepository.existsByKey("NP")).thenReturn(false);
        when(projectRepository.save(project)).thenReturn(project);
        when(projectMapper.toResponse(project)).thenReturn(sampleResponse());
        when(projectMemberRepository.existsByUserIdAndProjectId(creatorId, PROJECT_ID)).thenReturn(false);
        when(userService.findEntityById(creatorId)).thenReturn(Optional.of(creator));

        projectService.create(request, creatorId);

        org.mockito.ArgumentCaptor<ProjectMember> captor =
                org.mockito.ArgumentCaptor.forClass(ProjectMember.class);
        verify(projectMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(ProjectRole.ADMIN);
        assertThat(captor.getValue().getUser()).isEqualTo(creator);
    }

    @Test
    void create_collisionKey_appendsSuffix() {
        var request = new CreateProjectRequest("New Project", "Description");
        Project project = sampleProject();

        when(projectMapper.toEntity(request)).thenReturn(project);
        when(projectRepository.existsByKey("NP")).thenReturn(true);
        when(projectRepository.existsByKey("NP2")).thenReturn(false);
        when(projectRepository.save(project)).thenReturn(project);
        when(projectMapper.toResponse(project)).thenReturn(sampleResponse());

        ProjectResponse result = projectService.create(request, null);

        assertThat(result).isNotNull();
        assertThat(project.getKey()).isEqualTo("NP2");
    }

    @Test
    void update_updatesAndReturnsProject() {
        var request = new UpdateProjectRequest("Updated Name", "Updated desc");
        Project project = sampleProject();

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectRepository.save(project)).thenReturn(project);
        when(projectMapper.toResponse(project)).thenReturn(sampleResponse());

        ProjectResponse result = projectService.update(PROJECT_ID, request, null);

        assertThat(result).isNotNull();
        verify(projectMapper).updateEntity(request, project);
    }

    @Test
    void delete_removesProject() {
        Project project = sampleProject();
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(testRunRepository.findByProjectIdOrderByCreatedAtDesc(PROJECT_ID))
                .thenReturn(java.util.List.of());

        projectService.delete(PROJECT_ID, null);

        verify(projectRepository).delete(project);
    }

    @Test
    void delete_notFound_throwsException() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.delete(PROJECT_ID, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
