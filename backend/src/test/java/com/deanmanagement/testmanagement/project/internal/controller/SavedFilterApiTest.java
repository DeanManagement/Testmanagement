package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.SavedFilterRepository;
import com.deanmanagement.testmanagement.project.internal.service.ProjectService;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Saved and shared filters end to end (PRD-053 §3). Not transactional, so each request commits on
 * its own and a later read proves the earlier write was stored.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class SavedFilterApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectService projectService;
    @Autowired private SavedFilterRepository savedFilterRepository;

    private Project project;
    private UUID alice;
    private UUID bob;
    private UUID admin;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setName("Saved filters");
        project.setKey("SF" + Integer.toHexString(new Random().nextInt(0xFFFFF)).toUpperCase());
        project = projectRepository.save(project);
        alice = member(ProjectRole.VIEWER);
        bob = member(ProjectRole.TESTER);
        admin = member(ProjectRole.ADMIN);
    }

    @AfterEach
    void tearDown() {
        projectService.delete(project.getId(), null);
    }

    private UUID member(ProjectRole role) {
        User user = new User();
        user.setEmail("sf-" + UUID.randomUUID() + "@test.local");
        user.setDisplayName("u");
        user.setPasswordHash("x");
        user = userRepository.save(user);
        ProjectMember m = new ProjectMember();
        m.setUser(user);
        m.setProject(project);
        m.setRole(role);
        memberRepository.save(m);
        return user.getId();
    }

    private static RequestPostProcessor as(UUID userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private String url() {
        return "/api/projects/" + project.getId() + "/saved-filters";
    }

    private String save(UUID owner, String name, String queryString, boolean shared) throws Exception {
        String body = """
                {"name": "%s", "view": "TEST_CASES", "queryString": "%s", "shared": %s}
                """.formatted(name, queryString, shared);
        String response = mockMvc.perform(post(url()).with(as(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    @Test
    void aSavedFilterKeepsTheQueryStringWithoutItsQuestionMark() throws Exception {
        save(alice, "Open highs", "?status=READY&priority=HIGH&label=a&label=b", false);

        mockMvc.perform(get(url()).param("view", "TEST_CASES").with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].queryString").value("status=READY&priority=HIGH&label=a&label=b"))
                .andExpect(jsonPath("$[0].mine").value(true))
                .andExpect(jsonPath("$[0].editable").value(true));
    }

    @Test
    void aPrivateFilterIsInvisibleToOtherMembers() throws Exception {
        save(alice, "Mine", "q=pay", false);

        mockMvc.perform(get(url()).param("view", "TEST_CASES").with(as(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void aSharedFilterIsVisibleButNotEditableForOtherMembers() throws Exception {
        String id = save(alice, "Team", "q=pay", true);

        mockMvc.perform(get(url()).param("view", "TEST_CASES").with(as(bob)))
                .andExpect(jsonPath("$[*].name", contains("Team")))
                .andExpect(jsonPath("$[0].mine").value(false))
                .andExpect(jsonPath("$[0].editable").value(false));
        mockMvc.perform(delete(url() + "/" + id).with(as(bob)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aProjectAdminMayRenameAndDeleteASharedFilter() throws Exception {
        String id = save(alice, "Team", "q=pay", true);

        mockMvc.perform(put(url() + "/" + id).with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Payments\", \"shared\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Payments"));
        mockMvc.perform(delete(url() + "/" + id).with(as(admin)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(savedFilterRepository.findById(UUID.fromString(id))).isEmpty();
    }

    @Test
    void aProjectAdminCannotReachSomeoneElsesPrivateFilter() throws Exception {
        String id = save(alice, "Mine", "q=pay", false);

        mockMvc.perform(delete(url() + "/" + id).with(as(admin)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void theOwnerCanUnshareTheirFilter() throws Exception {
        String id = save(alice, "Team", "q=pay", true);

        mockMvc.perform(put(url() + "/" + id).with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Team\", \"shared\": false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(url()).param("view", "TEST_CASES").with(as(bob)))
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void aNonMemberCannotListTheProjectsFilters() throws Exception {
        User stranger = new User();
        stranger.setEmail("sf-" + UUID.randomUUID() + "@test.local");
        stranger.setDisplayName("s");
        stranger.setPasswordHash("x");
        UUID strangerId = userRepository.save(stranger).getId();

        mockMvc.perform(get(url()).param("view", "TEST_CASES").with(as(strangerId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnknownViewIsRejected() throws Exception {
        mockMvc.perform(get(url()).param("view", "NOPE").with(as(alice)))
                .andExpect(status().isBadRequest());
    }
}
