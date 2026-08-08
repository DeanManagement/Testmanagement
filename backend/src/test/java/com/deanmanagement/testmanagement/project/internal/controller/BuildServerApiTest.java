package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.apiKey.CreateApiKeyRequest;
import com.deanmanagement.testmanagement.project.internal.entity.BuildServerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.BuildServerProviderType;
import com.deanmanagement.testmanagement.project.internal.entity.BuildWorkflow;
import com.deanmanagement.testmanagement.project.internal.entity.PipelineRun;
import com.deanmanagement.testmanagement.project.internal.entity.PipelineRunStatus;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectBuildWorkflow;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.BuildServerConfigRepository;
import com.deanmanagement.testmanagement.project.internal.repository.BuildWorkflowRepository;
import com.deanmanagement.testmanagement.project.internal.repository.PipelineRunRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectBuildWorkflowRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD-024 API behaviour. The load-bearing assertions: only system admins touch the global
 * registry, the token never comes back out, a project sees exactly its assigned workflows with no
 * server internals, an unassigned workflow is not-found (not forbidden), and a reported result
 * carrying a pipelineRunId links the created test run — but never across projects.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class BuildServerApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private BuildServerConfigRepository configRepository;
    @Autowired
    private BuildWorkflowRepository workflowRepository;
    @Autowired
    private ProjectBuildWorkflowRepository assignmentRepository;
    @Autowired
    private PipelineRunRepository pipelineRunRepository;
    @Autowired
    private ApiKeyService apiKeyService;

    private static final String TOKEN = "not-a-real-build-token";
    private static final String JUNIT_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="suite" tests="1">
              <testcase classname="suite" name="passes"/>
            </testsuite>
            """;

    private String sysAdmin;
    private String tester;
    private String viewer;
    private Project project;
    private Project otherProject;

    @BeforeEach
    void setUp() {
        sysAdmin = saveUser().getId().toString();
        User testerUser = saveUser();
        User viewerUser = saveUser();
        tester = testerUser.getId().toString();
        viewer = viewerUser.getId().toString();

        project = saveProject("Automated", "AUT");
        otherProject = saveProject("Other", "OTB");
        saveMember(testerUser, project, ProjectRole.TESTER);
        saveMember(viewerUser, project, ProjectRole.VIEWER);
    }

    // ---- Fixtures ---------------------------------------------------------

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

    private void saveMember(User user, Project proj, ProjectRole role) {
        ProjectMember pm = new ProjectMember();
        pm.setUser(user);
        pm.setProject(proj);
        pm.setRole(role);
        projectMemberRepository.save(pm);
    }

    private BuildServerConfig saveServer() {
        BuildServerConfig config = new BuildServerConfig();
        config.setName("Stub GitLab " + UUID.randomUUID());
        config.setProvider(BuildServerProviderType.GITLAB_CI);
        config.setBaseUrl("https://gitlab.example.com");
        config.setApiTokenEncrypted("irrelevant-ciphertext");
        return configRepository.save(config);
    }

    private BuildWorkflow saveWorkflow(BuildServerConfig config, String name) {
        BuildWorkflow workflow = new BuildWorkflow();
        workflow.setBuildServerConfig(config);
        workflow.setName(name);
        workflow.setRepoRef("group/project");
        workflow.setDefaultRef("main");
        return workflowRepository.save(workflow);
    }

    private void assign(Project proj, BuildWorkflow workflow) {
        ProjectBuildWorkflow assignment = new ProjectBuildWorkflow();
        assignment.setProjectId(proj.getId());
        assignment.setWorkflow(workflow);
        assignmentRepository.save(assignment);
    }

    private PipelineRun savePipelineRun(Project proj, BuildWorkflow workflow) {
        PipelineRun run = new PipelineRun();
        run.setWorkflow(workflow);
        run.setProjectId(proj.getId());
        run.setWorkflowName(workflow.getName());
        run.setStatus(PipelineRunStatus.RUNNING);
        return pipelineRunRepository.save(run);
    }

    private String serverBody() {
        return "{\"name\":\"Company GitLab\",\"provider\":\"GITLAB_CI\","
                + "\"baseUrl\":\"https://gitlab.example.com\",\"apiToken\":\"" + TOKEN + "\"}";
    }

    // ---- Global admin authz & token hygiene --------------------------------

    @Test
    void nonAdmin_cannotTouchTheGlobalRegistry() throws Exception {
        mockMvc.perform(get("/api/build-servers").with(user(tester)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/build-servers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serverBody())
                        .with(user(tester)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesServer_tokenIsNeverReturned_andEncryptedAtRest() throws Exception {
        String response = mockMvc.perform(post("/api/build-servers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serverBody())
                        .with(user(sysAdmin).roles("ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenSet").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(TOKEN);
        assertThat(response).doesNotContain("apiToken");

        BuildServerConfig stored = configRepository.findByName("Company GitLab").orElseThrow();
        assertThat(stored.getApiTokenEncrypted()).isNotBlank();
        assertThat(stored.getApiTokenEncrypted()).doesNotContain(TOKEN);
    }

    // URL/SSRF rules are covered DNS-free in BuildServerUrlValidatorTest; the test profile runs
    // with allow-private-targets so stub servers on 127.0.0.1 (and unresolvable example.com
    // fixtures) work on CI runners without outbound DNS.

    @Test
    void workflowAssignments_areReplacedAsASet() throws Exception {
        BuildServerConfig config = saveServer();
        BuildWorkflow workflow = saveWorkflow(config, "Nightly");
        assign(otherProject, workflow);

        mockMvc.perform(put("/api/build-servers/workflows/" + workflow.getId() + "/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectIds\":[\"" + project.getId() + "\"]}")
                        .with(user(sysAdmin).roles("ADMIN")))
                .andExpect(status().isNoContent());

        assertThat(assignmentRepository.findByWorkflowId(workflow.getId()))
                .extracting(ProjectBuildWorkflow::getProjectId)
                .containsExactly(project.getId());
    }

    // ---- Project-side visibility & trigger authz ---------------------------

    @Test
    void projectSeesOnlyAssignedWorkflows_withoutServerInternals() throws Exception {
        BuildServerConfig config = saveServer();
        BuildWorkflow assigned = saveWorkflow(config, "Assigned suite");
        saveWorkflow(config, "Unassigned suite");
        assign(project, assigned);

        mockMvc.perform(get("/api/projects/" + project.getId() + "/workflows").with(user(tester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Assigned suite"))
                .andExpect(content().string(not(containsString("gitlab.example.com"))))
                .andExpect(content().string(not(containsString("group/project"))));
    }

    @Test
    void triggeringAnUnassignedWorkflow_isNotFound() throws Exception {
        BuildServerConfig config = saveServer();
        BuildWorkflow workflow = saveWorkflow(config, "Nightly");
        assign(otherProject, workflow);

        mockMvc.perform(post("/api/projects/" + project.getId() + "/workflows/"
                        + workflow.getId() + "/trigger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user(tester)))
                .andExpect(status().isNotFound());
    }

    @Test
    void viewer_cannotTrigger() throws Exception {
        BuildServerConfig config = saveServer();
        BuildWorkflow workflow = saveWorkflow(config, "Nightly");
        assign(project, workflow);

        mockMvc.perform(post("/api/projects/" + project.getId() + "/workflows/"
                        + workflow.getId() + "/trigger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user(viewer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void pipelineRunListing_isProjectScoped() throws Exception {
        BuildServerConfig config = saveServer();
        BuildWorkflow workflow = saveWorkflow(config, "Nightly");
        assign(project, workflow);
        assign(otherProject, workflow);
        savePipelineRun(project, workflow);
        savePipelineRun(otherProject, workflow);

        mockMvc.perform(get("/api/projects/" + project.getId() + "/pipeline-runs").with(user(tester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    // ---- Report-back correlation -------------------------------------------

    @Test
    void reportedResults_linkTheTestRun_andNameItAfterTheWorkflow() throws Exception {
        BuildServerConfig config = saveServer();
        BuildWorkflow workflow = saveWorkflow(config, "Nightly regression");
        assign(project, workflow);
        PipelineRun pipelineRun = savePipelineRun(project, workflow);
        String apiKey = apiKeyService.create(new CreateApiKeyRequest("ci", project.getId())).rawKey();

        mockMvc.perform(post("/api/external/projects/AUT/test-runs/junit")
                        .header("X-API-Key", apiKey)
                        .param("pipelineRunId", pipelineRun.getId().toString())
                        .contentType(MediaType.APPLICATION_XML)
                        .content(JUNIT_XML))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Nightly regression"));

        PipelineRun linked = pipelineRunRepository.findById(pipelineRun.getId()).orElseThrow();
        assertThat(linked.getTestRun()).isNotNull();
        assertThat(linked.getTestRun().getName()).isEqualTo("Nightly regression");
    }

    @Test
    void foreignPipelineRunId_isRejectedAsNotFound() throws Exception {
        BuildServerConfig config = saveServer();
        BuildWorkflow workflow = saveWorkflow(config, "Nightly");
        assign(otherProject, workflow);
        PipelineRun foreignRun = savePipelineRun(otherProject, workflow);
        String apiKey = apiKeyService.create(new CreateApiKeyRequest("ci", project.getId())).rawKey();

        // The key is scoped to AUT; the pipeline run belongs to OTB. Existence is not disclosed.
        mockMvc.perform(post("/api/external/projects/AUT/test-runs/junit")
                        .header("X-API-Key", apiKey)
                        .param("pipelineRunId", foreignRun.getId().toString())
                        .contentType(MediaType.APPLICATION_XML)
                        .content(JUNIT_XML))
                .andExpect(status().isNotFound());

        assertThat(pipelineRunRepository.findById(foreignRun.getId()).orElseThrow().getTestRun())
                .isNull();
    }
}
