package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.ExternalCreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.ExternalTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.project.internal.service.ExternalTestRunService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExternalTestRunController.class)
class ExternalTestRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ExternalTestRunService externalTestRunService;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID TEST_CASE_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    private TestRunResponse sampleRunResponse() {
        return new TestRunResponse(
                UUID.randomUUID(), "CI Run", "staging",
                TestRunStatus.COMPLETED, NOW, NOW,
                List.of(), NOW, NOW
        );
    }

    @Test
    @WithMockUser
    void create_returnsCreated() throws Exception {
        var request = new ExternalCreateTestRunRequest("CI Run", "staging", List.of(
                new ExternalTestResultRequest(TEST_CASE_ID, TestResultStatus.PASSED, null, null, null)
        ));
        when(externalTestRunService.createExternalRun(eq(PROJECT_ID), any())).thenReturn(sampleRunResponse());

        mockMvc.perform(post("/api/external/projects/{projectId}/test-runs", PROJECT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("CI Run"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser
    void create_emptyResults_returns400() throws Exception {
        var request = new ExternalCreateTestRunRequest("CI Run", "staging", List.of());

        mockMvc.perform(post("/api/external/projects/{projectId}/test-runs", PROJECT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void create_blankName_returns400() throws Exception {
        var request = new ExternalCreateTestRunRequest("", "staging", List.of(
                new ExternalTestResultRequest(TEST_CASE_ID, TestResultStatus.PASSED, null, null, null)
        ));

        mockMvc.perform(post("/api/external/projects/{projectId}/test-runs", PROJECT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void create_nullResults_returns400() throws Exception {
        String json = "{\"name\":\"CI Run\",\"environment\":\"staging\"}";

        mockMvc.perform(post("/api/external/projects/{projectId}/test-runs", PROJECT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void create_projectNotFound_returns404() throws Exception {
        var request = new ExternalCreateTestRunRequest("CI Run", "staging", List.of(
                new ExternalTestResultRequest(TEST_CASE_ID, TestResultStatus.PASSED, null, null, null)
        ));
        when(externalTestRunService.createExternalRun(eq(PROJECT_ID), any()))
                .thenThrow(new ResourceNotFoundException("Project", PROJECT_ID));

        mockMvc.perform(post("/api/external/projects/{projectId}/test-runs", PROJECT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        var request = new ExternalCreateTestRunRequest("CI Run", "staging", List.of(
                new ExternalTestResultRequest(TEST_CASE_ID, TestResultStatus.PASSED, null, null, null)
        ));

        mockMvc.perform(post("/api/external/projects/{projectId}/test-runs", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
