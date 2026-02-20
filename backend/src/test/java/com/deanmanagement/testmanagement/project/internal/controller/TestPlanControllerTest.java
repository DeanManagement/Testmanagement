package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.testplan.CreateTestPlanRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.TestPlanResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.TestPlanSummaryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.UpdateTestPlanRequest;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlanStatus;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.project.internal.service.TestPlanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
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

@WebMvcTest(TestPlanController.class)
class TestPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TestPlanService testPlanService;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final String MOCK_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final Instant NOW = Instant.now();

    private TestPlanResponse sampleResponse() {
        return new TestPlanResponse(
                PLAN_ID, "Release 2.3", "Description",
                TestPlanStatus.OPEN, LocalDate.of(2026, 3, 15),
                2, NOW, NOW, null, null
        );
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void findAll_returnsPlans() throws Exception {
        when(testPlanService.findByProject(PROJECT_ID)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/projects/{projectId}/test-plans", PROJECT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Release 2.3"));
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void findById_returnsPlan() throws Exception {
        when(testPlanService.findById(PROJECT_ID, PLAN_ID)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/projects/{projectId}/test-plans/{id}", PROJECT_ID, PLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Release 2.3"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void findById_notFound_returns404() throws Exception {
        when(testPlanService.findById(PROJECT_ID, PLAN_ID))
                .thenThrow(new ResourceNotFoundException("TestPlan", PLAN_ID));

        mockMvc.perform(get("/api/projects/{projectId}/test-plans/{id}", PROJECT_ID, PLAN_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void getSummary_returnsSummary() throws Exception {
        var summary = new TestPlanSummaryResponse(
                PLAN_ID, "Release 2.3", TestPlanStatus.OPEN, LocalDate.of(2026, 3, 15),
                2, 1, 10, 8, 1, 0, 0, 1, 80.0, List.of()
        );
        when(testPlanService.getSummary(PROJECT_ID, PLAN_ID)).thenReturn(summary);

        mockMvc.perform(get("/api/projects/{projectId}/test-plans/{id}/summary", PROJECT_ID, PLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRuns").value(2))
                .andExpect(jsonPath("$.passRate").value(80.0));
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void create_returnsCreated() throws Exception {
        var request = new CreateTestPlanRequest("Release 2.3", "Description", LocalDate.of(2026, 3, 15));
        when(testPlanService.create(eq(PROJECT_ID), any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/projects/{projectId}/test-plans", PROJECT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Release 2.3"));
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void create_blankName_returns400() throws Exception {
        var request = new CreateTestPlanRequest("", null, null);

        mockMvc.perform(post("/api/projects/{projectId}/test-plans", PROJECT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void update_returnsPlan() throws Exception {
        var request = new UpdateTestPlanRequest("Updated", "Desc", TestPlanStatus.IN_PROGRESS, null);
        when(testPlanService.update(eq(PROJECT_ID), eq(PLAN_ID), any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/projects/{projectId}/test-plans/{id}", PROJECT_ID, PLAN_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Release 2.3"));
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void delete_returnsNoContent() throws Exception {
        doNothing().when(testPlanService).delete(eq(PROJECT_ID), eq(PLAN_ID), any());

        mockMvc.perform(delete("/api/projects/{projectId}/test-plans/{id}", PROJECT_ID, PLAN_ID)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void delete_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("TestPlan", PLAN_ID))
                .when(testPlanService).delete(eq(PROJECT_ID), eq(PLAN_ID), any());

        mockMvc.perform(delete("/api/projects/{projectId}/test-plans/{id}", PROJECT_ID, PLAN_ID)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void findAll_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/projects/{projectId}/test-plans", PROJECT_ID))
                .andExpect(status().isUnauthorized());
    }
}
