package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.project.CreateProjectRequest;
import com.deanmanagement.testmanagement.project.internal.dto.project.ProjectResponse;
import com.deanmanagement.testmanagement.project.internal.dto.project.UpdateProjectRequest;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.project.internal.service.DashboardService;
import com.deanmanagement.testmanagement.project.internal.service.ProjectService;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.UserService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectController.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private ProjectMemberRepository projectMemberRepository;

    @MockitoBean
    private UserService userService;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    private ProjectResponse sampleResponse() {
        return new ProjectResponse(PROJECT_ID, "My Project", "A test project", "MYPRJ", false, NOW, NOW, null, null);
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void findAll_returnsProjects() throws Exception {
        when(projectService.findAll(any(UUID.class), anyBoolean())).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("My Project"))
                .andExpect(jsonPath("$[0].key").value("MYPRJ"));
    }

    @Test
    @WithMockUser
    void findById_returnsProject() throws Exception {
        when(projectService.findById(PROJECT_ID)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/projects/{id}", PROJECT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My Project"));
    }

    @Test
    @WithMockUser
    void findById_notFound_returns404() throws Exception {
        when(projectService.findById(PROJECT_ID))
                .thenThrow(new ResourceNotFoundException("Project", PROJECT_ID));

        mockMvc.perform(get("/api/projects/{id}", PROJECT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "ADMIN")
    void create_returnsCreated() throws Exception {
        var request = new CreateProjectRequest("New Project", "Description");
        when(projectService.create(any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("My Project"));
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void create_nonAdmin_returns403() throws Exception {
        var request = new CreateProjectRequest("New Project", "Description");

        mockMvc.perform(post("/api/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_invalidRequest_returns400() throws Exception {
        var request = new CreateProjectRequest("", null);

        mockMvc.perform(post("/api/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void update_returnsUpdated() throws Exception {
        var request = new UpdateProjectRequest("Updated Name", "Updated desc");
        when(projectService.update(eq(PROJECT_ID), any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/projects/{id}", PROJECT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void delete_returnsNoContent() throws Exception {
        doNothing().when(projectService).delete(eq(PROJECT_ID), any());

        mockMvc.perform(delete("/api/projects/{id}", PROJECT_ID)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void delete_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Project", PROJECT_ID))
                .when(projectService).delete(eq(PROJECT_ID), any());

        mockMvc.perform(delete("/api/projects/{id}", PROJECT_ID)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void toggleBugReports_asProjectAdmin_returnsOk() throws Exception {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ProjectMember member = new ProjectMember();
        member.setRole(ProjectRole.ADMIN);
        when(projectMemberRepository.findByUserIdAndProjectId(userId, PROJECT_ID))
                .thenReturn(Optional.of(member));
        when(projectService.toggleBugReports(eq(PROJECT_ID), eq(true), eq(userId)))
                .thenReturn(sampleResponse());

        mockMvc.perform(put("/api/projects/{id}/settings/bug-reports", PROJECT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void toggleBugReports_asNonAdmin_returns403() throws Exception {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(projectMemberRepository.findByUserIdAndProjectId(userId, PROJECT_ID))
                .thenReturn(Optional.empty());
        when(userService.findEntityById(userId))
                .thenReturn(Optional.of(createNonAdminUser()));

        mockMvc.perform(put("/api/projects/{id}/settings/bug-reports", PROJECT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void toggleBugReports_asSystemAdmin_returnsOk() throws Exception {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(projectMemberRepository.findByUserIdAndProjectId(userId, PROJECT_ID))
                .thenReturn(Optional.empty());
        User systemAdmin = new User();
        systemAdmin.setSystemAdmin(true);
        when(userService.findEntityById(userId)).thenReturn(Optional.of(systemAdmin));
        when(projectService.toggleBugReports(eq(PROJECT_ID), eq(true), eq(userId)))
                .thenReturn(sampleResponse());

        mockMvc.perform(put("/api/projects/{id}/settings/bug-reports", PROJECT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().isOk());
    }

    private User createNonAdminUser() {
        User user = new User();
        user.setSystemAdmin(false);
        return user;
    }
}
