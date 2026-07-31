package com.deanmanagement.testmanagement.project.internal.access;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end authorization sweep for PRD-001. Boots the full context (so the {@code @RequireProjectRole}
 * aspect is active) against H2 and exercises representative read/write endpoints across the role matrix.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class ProjectAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    private UUID projectId;
    private String viewer;
    private String tester;
    private String admin;
    private String nonMember;
    private String sysAdmin;

    private static final String TEST_CASE_BODY =
            "{\"title\":\"Login works\",\"priority\":\"MEDIUM\",\"status\":\"ACTIVE\"}";
    private static final String TEST_SUITE_BODY =
            "{\"name\":\"Smoke\",\"description\":\"d\"}";
    private static final String PROJECT_BODY =
            "{\"name\":\"Renamed\",\"description\":\"d\"}";

    @BeforeEach
    void setUp() {
        viewer = newUser("viewer", false).toString();
        tester = newUser("tester", false).toString();
        admin = newUser("admin", false).toString();
        nonMember = newUser("outsider", false).toString();
        sysAdmin = newUser("sysadmin", true).toString();

        Project project = new Project();
        project.setName("Auth Project");
        project.setKey("AUTHP");
        project = projectRepository.save(project);
        projectId = project.getId();

        addMember(project, UUID.fromString(viewer), ProjectRole.VIEWER);
        addMember(project, UUID.fromString(tester), ProjectRole.TESTER);
        addMember(project, UUID.fromString(admin), ProjectRole.ADMIN);
    }

    private UUID newUser(String name, boolean systemAdmin) {
        User u = new User();
        u.setEmail(name + "-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName(name);
        u.setPasswordHash("x");
        u.setSystemAdmin(systemAdmin);
        return userRepository.save(u).getId();
    }

    private void addMember(Project project, UUID userId, ProjectRole role) {
        ProjectMember m = new ProjectMember();
        m.setProject(project);
        m.setUser(userRepository.findById(userId).orElseThrow());
        m.setRole(role);
        projectMemberRepository.save(m);
    }

    // ---- Project read (VIEWER) ----

    @Test
    void readProject_member_ok() throws Exception {
        mockMvc.perform(get("/api/projects/{id}", projectId).with(user(viewer)))
                .andExpect(status().isOk());
    }

    @Test
    void readProject_nonMember_forbidden() throws Exception {
        mockMvc.perform(get("/api/projects/{id}", projectId).with(user(nonMember)))
                .andExpect(status().isForbidden());
    }

    @Test
    void readProject_systemAdmin_ok() throws Exception {
        mockMvc.perform(get("/api/projects/{id}", projectId).with(user(sysAdmin)))
                .andExpect(status().isOk());
    }

    // ---- Project update (ADMIN) ----

    @Test
    void updateProject_viewer_forbidden() throws Exception {
        mockMvc.perform(put("/api/projects/{id}", projectId).with(user(viewer))
                        .contentType("application/json").content(PROJECT_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProject_tester_forbidden() throws Exception {
        mockMvc.perform(put("/api/projects/{id}", projectId).with(user(tester))
                        .contentType("application/json").content(PROJECT_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProject_admin_ok() throws Exception {
        mockMvc.perform(put("/api/projects/{id}", projectId).with(user(admin))
                        .contentType("application/json").content(PROJECT_BODY))
                .andExpect(status().isOk());
    }

    // ---- Project delete (ADMIN) ----

    @Test
    void deleteProject_tester_forbidden() throws Exception {
        mockMvc.perform(delete("/api/projects/{id}", projectId).with(user(tester)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteProject_admin_ok() throws Exception {
        mockMvc.perform(delete("/api/projects/{id}", projectId).with(user(admin)))
                .andExpect(status().isNoContent());
    }

    // ---- Test cases read (VIEWER) ----

    @Test
    void readTestCases_member_ok() throws Exception {
        mockMvc.perform(get("/api/projects/{p}/test-cases", projectId).with(user(viewer)))
                .andExpect(status().isOk());
    }

    @Test
    void readTestCases_nonMember_forbidden() throws Exception {
        mockMvc.perform(get("/api/projects/{p}/test-cases", projectId).with(user(nonMember)))
                .andExpect(status().isForbidden());
    }

    // ---- Test cases create (TESTER) ----

    @Test
    void createTestCase_viewer_forbidden() throws Exception {
        mockMvc.perform(post("/api/projects/{p}/test-cases", projectId).with(user(viewer))
                        .contentType("application/json").content(TEST_CASE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTestCase_nonMember_forbidden() throws Exception {
        mockMvc.perform(post("/api/projects/{p}/test-cases", projectId).with(user(nonMember))
                        .contentType("application/json").content(TEST_CASE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTestSuite_tester_created() throws Exception {
        mockMvc.perform(post("/api/projects/{p}/test-suites", projectId).with(user(tester))
                        .contentType("application/json").content(TEST_SUITE_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void createTestSuite_viewer_forbidden() throws Exception {
        mockMvc.perform(post("/api/projects/{p}/test-suites", projectId).with(user(viewer))
                        .contentType("application/json").content(TEST_SUITE_BODY))
                .andExpect(status().isForbidden());
    }

    // ---- Import / export (export VIEWER, import TESTER) ----

    @Test
    void exportTestCases_member_ok() throws Exception {
        mockMvc.perform(get("/api/projects/{p}/test-cases/export", projectId)
                        .param("format", "json").with(user(viewer)))
                .andExpect(status().isOk());
    }

    @Test
    void importTestCases_viewer_forbidden() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "t.csv", "text/csv", "title\nFoo\n".getBytes());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/projects/{p}/test-cases/import", projectId)
                        .file(file).with(user(viewer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void importTestCases_tester_ok() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "t.csv", "text/csv", "title\nFoo\n".getBytes());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/projects/{p}/test-cases/import", projectId)
                        .file(file).with(user(tester)))
                .andExpect(status().isOk());
    }

    // ---- Webhooks (ADMIN only) ----

    @Test
    void listWebhooks_viewer_forbidden() throws Exception {
        mockMvc.perform(get("/api/projects/{p}/webhooks", projectId).with(user(viewer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listWebhooks_tester_forbidden() throws Exception {
        mockMvc.perform(get("/api/projects/{p}/webhooks", projectId).with(user(tester)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listWebhooks_admin_ok() throws Exception {
        mockMvc.perform(get("/api/projects/{p}/webhooks", projectId).with(user(admin)))
                .andExpect(status().isOk());
    }
}
