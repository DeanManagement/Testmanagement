package com.deanmanagement.testmanagement.controller;

import com.deanmanagement.testmanagement.dto.CreateProjectRequest;
import com.deanmanagement.testmanagement.dto.ProjectResponse;
import com.deanmanagement.testmanagement.dto.UpdateProjectRequest;
import com.deanmanagement.testmanagement.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.service.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    private ProjectResponse sampleResponse() {
        return new ProjectResponse(PROJECT_ID, "My Project", "A test project", "MYPRJ", NOW, NOW);
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
    @WithMockUser
    void create_returnsCreated() throws Exception {
        var request = new CreateProjectRequest("New Project", "Description");
        when(projectService.create(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("My Project"));
    }

    @Test
    @WithMockUser
    void create_invalidRequest_returns400() throws Exception {
        var request = new CreateProjectRequest("", null);

        mockMvc.perform(post("/api/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void update_returnsUpdated() throws Exception {
        var request = new UpdateProjectRequest("Updated Name", "Updated desc");
        when(projectService.update(eq(PROJECT_ID), any())).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/projects/{id}", PROJECT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void delete_returnsNoContent() throws Exception {
        doNothing().when(projectService).delete(PROJECT_ID);

        mockMvc.perform(delete("/api/projects/{id}", PROJECT_ID)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void delete_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Project", PROJECT_ID))
                .when(projectService).delete(PROJECT_ID);

        mockMvc.perform(delete("/api/projects/{id}", PROJECT_ID)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }
}
