package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD-050: the case page's context (who made it, where it sits, which suites include it) and its
 * execution history, both scoped to the project and readable by any member.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class TestCaseContextApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private EntityManager entityManager;

    private String tess;
    private String viewer;
    private String outsider;
    private UUID projectId;
    private UUID otherProjectId;
    private UUID caseId;

    @BeforeEach
    void setUp() throws Exception {
        Project project = project("Context", "CTX");
        projectId = project.getId();
        tess = member(project, "Tess", ProjectRole.TESTER);
        viewer = member(project, "Vic", ProjectRole.VIEWER);
        outsider = saveUser("Otto").getId().toString();
        otherProjectId = project("Other", "OCX").getId();

        UUID parent = create("/test-case-folders", "{\"name\":\"Checkout\"}");
        UUID child = create("/test-case-folders", "{\"name\":\"Payment\",\"parentId\":\"" + parent + "\"}");
        caseId = create("/test-cases", "{\"title\":\"Pay by card\",\"priority\":\"HIGH\",\"status\":\"DRAFT\","
                + "\"folderId\":\"" + child + "\"}");
    }

    @Test
    void theContextNamesTheCreatorTheFolderPathAndTheSuitesByName() throws Exception {
        create("/test-suites", "{\"name\":\"Smoke\",\"testCaseIds\":[\"" + caseId + "\"]}");
        create("/test-suites", "{\"name\":\"Regression\",\"testCaseIds\":[\"" + caseId + "\"]}");
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get(url("/test-cases/" + caseId + "/context")).with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdByName").value("Tess"))
                .andExpect(jsonPath("$.folderPath[*].name", contains("Checkout", "Payment")))
                .andExpect(jsonPath("$.suites[*].name", contains("Regression", "Smoke")));
    }

    @Test
    void theHistoryListsRunsNewestFirstWithPendingOnesAndTheExecutor() throws Exception {
        UUID first = run("Nightly 1");
        UUID second = run("Nightly 2");
        record(first, "FAILED");

        mockMvc.perform(get(url("/test-cases/" + caseId + "/executions")).with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].runName", contains("Nightly 2", "Nightly 1")))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].executorName").doesNotExist())
                .andExpect(jsonPath("$.content[1].status").value("FAILED"))
                .andExpect(jsonPath("$.content[1].executorName").value("Tess"))
                .andExpect(jsonPath("$.content[1].runKey").value("CTX-Run-1"))
                .andExpect(jsonPath("$.content[1].executedVersion").isNumber());
    }

    @Test
    void theHistoryPagesAndCapsThePageSize() throws Exception {
        run("A");
        run("B");
        run("C");

        mockMvc.perform(get(url("/test-cases/" + caseId + "/executions?size=2")).with(user(viewer)))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(3));
        mockMvc.perform(get(url("/test-cases/" + caseId + "/executions?size=5000")).with(user(viewer)))
                .andExpect(jsonPath("$.page.size").value(100));
    }

    @Test
    void aCaseOfAnotherProjectIsNotFound() throws Exception {
        String otherViewer = member(projectRepository.findById(otherProjectId).orElseThrow(), "Vic3", ProjectRole.VIEWER);
        mockMvc.perform(get("/api/projects/" + otherProjectId + "/test-cases/" + caseId + "/context").with(user(otherViewer)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects/" + otherProjectId + "/test-cases/" + caseId + "/executions").with(user(otherViewer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aNonMemberCannotRead() throws Exception {
        mockMvc.perform(get(url("/test-cases/" + caseId + "/context")).with(user(outsider)))
                .andExpect(status().isForbidden());
    }

    // ---- helpers --------------------------------------------------------------------------------

    private UUID run(String name) throws Exception {
        return create("/test-runs", "{\"name\":\"" + name + "\",\"testCaseIds\":[\"" + caseId + "\"]}");
    }

    private void record(UUID run, String status) throws Exception {
        String detail = mockMvc.perform(get(url("/test-runs/" + run)).with(user(tess))).andReturn().getResponse()
                .getContentAsString();
        List<String> ids = JsonPath.read(detail, "$.results[*].id");
        send(put(url("/test-runs/" + run + "/results/" + ids.getFirst())), "{\"status\":\"" + status + "\"}")
                .andExpect(status().isOk());
    }

    private UUID create(String path, String body) throws Exception {
        String json = send(post(url(path)), body).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request.with(user(tess)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String url(String path) {
        return "/api/projects/" + projectId + path;
    }

    private Project project(String name, String key) {
        Project project = new Project();
        project.setName(name);
        project.setKey(key);
        return projectRepository.save(project);
    }

    private User saveUser(String name) {
        User user = new User();
        user.setEmail(name.toLowerCase() + "-" + UUID.randomUUID() + "@test.local");
        user.setDisplayName(name);
        user.setPasswordHash("x");
        return userRepository.save(user);
    }

    private String member(Project project, String name, ProjectRole role) {
        User user = saveUser(name);
        ProjectMember membership = new ProjectMember();
        membership.setUser(user);
        membership.setProject(project);
        membership.setRole(role);
        projectMemberRepository.save(membership);
        return user.getId().toString();
    }
}
