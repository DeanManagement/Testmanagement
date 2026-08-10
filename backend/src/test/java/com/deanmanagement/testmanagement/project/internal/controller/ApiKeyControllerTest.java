package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.apiKey.ApiKeyCreatedResponse;
import com.deanmanagement.testmanagement.project.internal.dto.apiKey.ApiKeyResponse;
import com.deanmanagement.testmanagement.project.internal.dto.apiKey.CreateApiKeyRequest;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiKeyController.class)
class ApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ApiKeyService apiKeyService;

    private static final UUID KEY_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    private ApiKeyResponse sampleResponse() {
        return new ApiKeyResponse(KEY_ID, "CI Pipeline", "tm_abc12", false, null, NOW, null, PROJECT_ID, "Demo Project", ProjectRole.TESTER);
    }

    private ApiKeyCreatedResponse sampleCreatedResponse() {
        return new ApiKeyCreatedResponse(KEY_ID, "CI Pipeline", "tm_abc12", "tm_abc123456789abcdef", NOW, PROJECT_ID, "Demo Project", ProjectRole.TESTER);
    }

    @Test
    @WithMockUser
    void rotate_returnsTheNewSecret() throws Exception {
        when(apiKeyService.rotate(KEY_ID)).thenReturn(sampleCreatedResponse());

        mockMvc.perform(post("/api/api-keys/{id}/rotate", KEY_ID).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawKey").value("tm_abc123456789abcdef"))
                .andExpect(jsonPath("$.id").value(KEY_ID.toString()));
    }

    @Test
    void rotate_requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/api-keys/{id}/rotate", KEY_ID).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void findAll_returnsApiKeys() throws Exception {
        when(apiKeyService.findAll()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("CI Pipeline"))
                .andExpect(jsonPath("$[0].keyPrefix").value("tm_abc12"))
                .andExpect(jsonPath("$[0].revoked").value(false));
    }

    @Test
    @WithMockUser
    void findAll_empty_returnsEmptyArray() throws Exception {
        when(apiKeyService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser
    void create_returnsCreatedWithRawKey() throws Exception {
        var request = new CreateApiKeyRequest("CI Pipeline", PROJECT_ID, ProjectRole.TESTER);
        when(apiKeyService.create(any())).thenReturn(sampleCreatedResponse());

        mockMvc.perform(post("/api/api-keys")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("CI Pipeline"))
                .andExpect(jsonPath("$.rawKey").value("tm_abc123456789abcdef"))
                .andExpect(jsonPath("$.keyPrefix").value("tm_abc12"));
    }

    @Test
    @WithMockUser
    void create_blankName_returns400() throws Exception {
        var request = new CreateApiKeyRequest("", PROJECT_ID, ProjectRole.TESTER);

        mockMvc.perform(post("/api/api-keys")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void revoke_returnsNoContent() throws Exception {
        doNothing().when(apiKeyService).revoke(KEY_ID);

        mockMvc.perform(delete("/api/api-keys/{id}", KEY_ID)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void revoke_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("ApiKey", KEY_ID))
                .when(apiKeyService).revoke(KEY_ID);

        mockMvc.perform(delete("/api/api-keys/{id}", KEY_ID)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void findAll_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/api-keys"))
                .andExpect(status().isUnauthorized());
    }
}
