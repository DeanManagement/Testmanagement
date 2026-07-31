package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Per-test-case permission overrides. The interesting cases are the boundaries: only project admins
 * may grant, the grantee must already be a member of the same project, and neither reads nor writes
 * may reach a test case belonging to a different project.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class TestCasePermissionApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private TestCaseRepository testCaseRepository;

    private String admin;
    private String tester;
    private String outsider;
    private UUID projectId;
    private UUID testCaseId;
    private UUID otherProjectId;
    private UUID testerUserId;
    private UUID outsiderUserId;

    @BeforeEach
    void setUp() {
        User adminUser = saveUser();
        User testerUser = saveUser();
        User outsiderUser = saveUser();
        admin = adminUser.getId().toString();
        tester = testerUser.getId().toString();
        outsider = outsiderUser.getId().toString();
        testerUserId = testerUser.getId();
        outsiderUserId = outsiderUser.getId();

        Project project = saveProject("Perm Project", "PERM");
        projectId = project.getId();
        otherProjectId = saveProject("Other Project", "OTHR").getId();

        saveMember(adminUser, project, ProjectRole.ADMIN);
        saveMember(testerUser, project, ProjectRole.TESTER);

        TestCase testCase = new TestCase();
        testCase.setProject(project);
        testCase.setKey("PERM-1");
        testCase.setTitle("A case");
        testCase.setPriority(Priority.MEDIUM);
        testCase.setStatus(TestCaseStatus.ACTIVE);
        testCaseId = testCaseRepository.save(testCase).getId();
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

    private String grantBody(UUID userId, boolean canEdit) {
        return "{\"userId\":\"" + userId + "\",\"canEdit\":" + canEdit + "}";
    }

    private String permissionsUrl(UUID project, UUID testCase) {
        return "/api/projects/" + project + "/test-cases/" + testCase + "/permissions";
    }

    @Test
    void admin_canGrantAndList() throws Exception {
        mockMvc.perform(post(permissionsUrl(projectId, testCaseId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(testerUserId, true))
                        .with(user(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.userId").value(testerUserId.toString()));

        mockMvc.perform(get(permissionsUrl(projectId, testCaseId)).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void regrant_updatesExistingRowInsteadOfDuplicating() throws Exception {
        mockMvc.perform(post(permissionsUrl(projectId, testCaseId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(testerUserId, false))
                        .with(user(admin)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(permissionsUrl(projectId, testCaseId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(testerUserId, true))
                        .with(user(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.canEdit").value(true));

        mockMvc.perform(get(permissionsUrl(projectId, testCaseId)).with(user(admin)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].canEdit").value(true));
    }

    @Test
    void tester_cannotGrant() throws Exception {
        mockMvc.perform(post(permissionsUrl(projectId, testCaseId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(testerUserId, true))
                        .with(user(tester)))
                .andExpect(status().isForbidden());
    }

    @Test
    void outsider_cannotList() throws Exception {
        mockMvc.perform(get(permissionsUrl(projectId, testCaseId)).with(user(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void grantingToNonMember_returns404() throws Exception {
        mockMvc.perform(post(permissionsUrl(projectId, testCaseId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(outsiderUserId, true))
                        .with(user(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCaseFromAnotherProject_returns404() throws Exception {
        saveMember(userRepository.findById(UUID.fromString(admin)).orElseThrow(),
                projectRepository.findById(otherProjectId).orElseThrow(), ProjectRole.ADMIN);

        mockMvc.perform(get(permissionsUrl(otherProjectId, testCaseId)).with(user(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void revokingPermissionFromAnotherTestCase_returns404() throws Exception {
        TestCase other = new TestCase();
        other.setProject(projectRepository.findById(projectId).orElseThrow());
        other.setKey("PERM-2");
        other.setTitle("Another case");
        other.setPriority(Priority.MEDIUM);
        other.setStatus(TestCaseStatus.ACTIVE);
        UUID otherTestCaseId = testCaseRepository.save(other).getId();

        String response = mockMvc.perform(post(permissionsUrl(projectId, testCaseId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantBody(testerUserId, true))
                        .with(user(admin)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String permissionId = response.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(delete(permissionsUrl(projectId, otherTestCaseId) + "/" + permissionId)
                        .with(user(admin)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(permissionsUrl(projectId, testCaseId) + "/" + permissionId)
                        .with(user(admin)))
                .andExpect(status().isNoContent());
    }
}
