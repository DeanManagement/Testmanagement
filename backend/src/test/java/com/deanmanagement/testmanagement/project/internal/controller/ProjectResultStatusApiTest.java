package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.project.CreateProjectRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.service.ProjectService;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TM24: the result-status settings over REST. */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class ProjectResultStatusApiTest {

    private static final String NOT_APPLICABLE =
            "{\"name\":\"Not applicable\",\"color\":\"#123456\",\"category\":\"SKIPPED\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectService projectService;

    private String admin;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        admin = newUser(true).getId().toString();
        projectId = projectService.create(new CreateProjectRequest("Statuses", null), UUID.fromString(admin)).id();
    }

    private User newUser(boolean systemAdmin) {
        User user = new User();
        user.setEmail("u-" + UUID.randomUUID() + "@test.local");
        user.setDisplayName("u");
        user.setPasswordHash("x");
        user.setSystemAdmin(systemAdmin);
        return userRepository.save(user);
    }

    private String member(ProjectRole role) {
        User user = newUser(false);
        ProjectMember membership = new ProjectMember();
        membership.setUser(user);
        membership.setProject(projectRepository.findById(projectId).orElseThrow());
        membership.setRole(role);
        projectMemberRepository.save(membership);
        return user.getId().toString();
    }

    @Test
    void aNewProjectStartsWithOneStatusPerCategory() throws Exception {
        mockMvc.perform(get("/api/projects/{p}/result-statuses", projectId).with(user(member(ProjectRole.VIEWER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].name").value("Pending"))
                .andExpect(jsonPath("$[1].category").value("PASSED"))
                .andExpect(jsonPath("$[4].name").value("Skipped"));
    }

    @Test
    void anAdminAddsAStatusAtTheEnd() throws Exception {
        mockMvc.perform(post("/api/projects/{p}/result-statuses", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(NOT_APPLICABLE).with(user(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("SKIPPED"))
                .andExpect(jsonPath("$.position").value(5));
    }

    @Test
    void aTesterCannotDefineStatuses() throws Exception {
        mockMvc.perform(post("/api/projects/{p}/result-statuses", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(NOT_APPLICABLE)
                        .with(user(member(ProjectRole.TESTER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aDuplicateNameIsRefused() throws Exception {
        mockMvc.perform(post("/api/projects/{p}/result-statuses", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" passed \",\"color\":\"#123456\",\"category\":\"PASSED\"}")
                        .with(user(admin)))
                .andExpect(status().isConflict());
    }

    @Test
    void theLastActiveStatusOfACategoryCannotBeArchived() throws Exception {
        String skippedId = com.jayway.jsonpath.JsonPath.read(mockMvc.perform(
                        get("/api/projects/{p}/result-statuses", projectId).with(user(admin)))
                .andReturn().getResponse().getContentAsString(), "$[4].id");

        mockMvc.perform(put("/api/projects/{p}/result-statuses/{id}", projectId, skippedId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}").with(user(admin)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/projects/{p}/result-statuses", projectId)
                .contentType(MediaType.APPLICATION_JSON).content(NOT_APPLICABLE).with(user(admin)));
        mockMvc.perform(put("/api/projects/{p}/result-statuses/{id}", projectId, skippedId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
    }
}
