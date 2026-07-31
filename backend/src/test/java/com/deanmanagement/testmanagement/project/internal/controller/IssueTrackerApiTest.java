package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.IssueTrackerConfigRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Config endpoint behaviour (PRD-010). The load-bearing assertions are that the API token never
 * comes back out, that it is not stored in the clear, and that only project admins can touch it.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class IssueTrackerApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private IssueTrackerConfigRepository configRepository;

    private static final String TOKEN = "glpat-SuperSecretTokenValue";

    private String admin;
    private String tester;
    private String outsider;
    private UUID projectId;
    private UUID otherProjectId;

    @BeforeEach
    void setUp() {
        User adminUser = saveUser();
        User testerUser = saveUser();
        admin = adminUser.getId().toString();
        tester = testerUser.getId().toString();
        outsider = saveUser().getId().toString();

        Project project = saveProject("Tracked", "TRK");
        projectId = project.getId();
        otherProjectId = saveProject("Other", "OTH").getId();

        saveMember(adminUser, project, ProjectRole.ADMIN);
        saveMember(testerUser, project, ProjectRole.TESTER);
    }

    private User saveUser() {
        User u = new User();
        u.setEmail("u-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName("u");
        u.setPasswordHash("x");
        u.setSystemAdmin(false);
        return userRepository.save(u);
    }

    private Project saveProject(String name, String key) {
        Project p = new Project();
        p.setName(name);
        p.setKey(key);
        return projectRepository.save(p);
    }

    private void saveMember(User user, Project project, ProjectRole role) {
        ProjectMember pm = new ProjectMember();
        pm.setUser(user);
        pm.setProject(project);
        pm.setRole(role);
        projectMemberRepository.save(pm);
    }

    private String configUrl(UUID project) {
        return "/api/projects/" + project + "/issue-tracker";
    }

    private String saveBody(String token) {
        String tokenPart = token == null ? "" : ",\"apiToken\":\"" + token + "\"";
        return "{\"provider\":\"GITLAB\",\"baseUrl\":\"https://gitlab.example.com\","
                + "\"projectRef\":\"group/project\"" + tokenPart + "}";
    }

    @Test
    void adminCanSaveConfig_andTokenIsNeverReturned() throws Exception {
        String response = mockMvc.perform(put(configUrl(projectId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(TOKEN))
                        .with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("GITLAB"))
                .andExpect(jsonPath("$.tokenSet").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(TOKEN);
        assertThat(response).doesNotContain("apiToken");

        mockMvc.perform(get(configUrl(projectId)).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(TOKEN))));
    }

    @Test
    void tokenIsEncryptedAtRest() throws Exception {
        mockMvc.perform(put(configUrl(projectId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(TOKEN))
                        .with(user(admin)))
                .andExpect(status().isOk());

        IssueTrackerConfig stored = configRepository.findByProjectId(projectId).orElseThrow();
        assertThat(stored.getApiTokenEncrypted()).isNotBlank();
        assertThat(stored.getApiTokenEncrypted()).doesNotContain(TOKEN);
    }

    @Test
    void updateWithoutTokenKeepsTheStoredOne() throws Exception {
        mockMvc.perform(put(configUrl(projectId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(TOKEN))
                        .with(user(admin)))
                .andExpect(status().isOk());
        String firstStored = configRepository.findByProjectId(projectId).orElseThrow().getApiTokenEncrypted();

        // Changing the project reference should not force the admin to re-paste the secret.
        mockMvc.perform(put(configUrl(projectId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"GITLAB\",\"baseUrl\":\"https://gitlab.example.com\","
                                + "\"projectRef\":\"group/renamed\"}")
                        .with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectRef").value("group/renamed"))
                .andExpect(jsonPath("$.tokenSet").value(true));

        assertThat(configRepository.findByProjectId(projectId).orElseThrow().getApiTokenEncrypted())
                .isEqualTo(firstStored);
    }

    @Test
    void firstSaveRequiresAToken() throws Exception {
        mockMvc.perform(put(configUrl(projectId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(null))
                        .with(user(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unconfiguredProjectReturnsNoContentRatherThanNotFound() throws Exception {
        mockMvc.perform(get(configUrl(projectId)).with(user(admin)))
                .andExpect(status().isNoContent());
    }

    @Test
    void testerCannotReadOrWriteConfig() throws Exception {
        mockMvc.perform(get(configUrl(projectId)).with(user(tester)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(configUrl(projectId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(TOKEN))
                        .with(user(tester)))
                .andExpect(status().isForbidden());
    }

    @Test
    void outsiderCannotReachAnotherProjectsConfig() throws Exception {
        mockMvc.perform(get(configUrl(projectId)).with(user(outsider)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(configUrl(otherProjectId)).with(user(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void supportedProvidersListsOnlyTrackersWithAnAdapter() throws Exception {
        mockMvc.perform(get(configUrl(projectId) + "/providers").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.containsInAnyOrder("GITLAB", "FORGEJO")));
    }

    @Test
    void forgejoCanBeConfigured() throws Exception {
        mockMvc.perform(put(configUrl(projectId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"FORGEJO\",\"baseUrl\":\"https://codeberg.org\","
                                + "\"projectRef\":\"acme/webshop\",\"apiToken\":\"" + TOKEN + "\"}")
                        .with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("FORGEJO"))
                .andExpect(jsonPath("$.projectRef").value("acme/webshop"))
                .andExpect(jsonPath("$.tokenSet").value(true));
    }

    @Test
    void unsupportedProviderIsRejectedUntilItHasAnAdapter() throws Exception {
        mockMvc.perform(put(configUrl(projectId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"JIRA\",\"baseUrl\":\"https://jira.example.com\","
                                + "\"projectRef\":\"PROJ\",\"apiToken\":\"" + TOKEN + "\"}")
                        .with(user(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRemovesTheConfig() throws Exception {
        mockMvc.perform(put(configUrl(projectId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(TOKEN))
                        .with(user(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(delete(configUrl(projectId)).with(user(admin)))
                .andExpect(status().isNoContent());

        assertThat(configRepository.findByProjectId(projectId)).isEmpty();
    }

    @Test
    void searchWithoutAConfiguredTrackerFailsCleanly() throws Exception {
        mockMvc.perform(get("/api/projects/" + projectId + "/issues/search")
                        .param("q", "login")
                        .with(user(tester)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankSearchMakesNoProviderCall() throws Exception {
        // Returns empty before touching the config, so an empty typeahead cannot 400 on projects
        // that have no tracker.
        mockMvc.perform(get("/api/projects/" + projectId + "/issues/search")
                        .with(user(tester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
