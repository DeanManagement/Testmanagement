package com.deanmanagement.testmanagement.project.internal.controller;

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
import com.deanmanagement.testmanagement.shared.crypto.AesGcmCipher;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full trigger round-trip against a stub Woodpecker (PRD-024 §3.3).
 *
 * <p>Deliberately <em>not</em> {@code @Transactional}: the trigger service runs outside a
 * transaction by design (no HTTP inside a DB transaction), and a transactional test would keep a
 * session open and mask exactly the class of bug this covers — the production
 * LazyInitializationException on the assignment's lazily loaded workflow.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class PipelineTriggerE2eTest {

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
    private AesGcmCipher secretCipher;

    private HttpServer stub;
    private final AtomicReference<String> triggerBody = new AtomicReference<>();

    private User testerUser;
    private Project project;
    private BuildServerConfig config;
    private BuildWorkflow workflow;

    @BeforeEach
    void setUp() throws IOException {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", exchange -> {
            triggerBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"number\": 7, \"status\": \"pending\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        stub.start();

        testerUser = new User();
        testerUser.setEmail("trigger-" + UUID.randomUUID() + "@test.local");
        testerUser.setDisplayName("t");
        testerUser.setPasswordHash("x");
        testerUser.setSystemAdmin(false);
        testerUser = userRepository.save(testerUser);

        project = new Project();
        project.setName("Trigger E2E");
        project.setKey("TRG" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        project = projectRepository.save(project);

        ProjectMember member = new ProjectMember();
        member.setUser(testerUser);
        member.setProject(project);
        member.setRole(ProjectRole.TESTER);
        projectMemberRepository.save(member);

        config = new BuildServerConfig();
        config.setName("Stub Woodpecker " + UUID.randomUUID());
        config.setProvider(BuildServerProviderType.WOODPECKER);
        config.setBaseUrl("http://127.0.0.1:" + stub.getAddress().getPort());
        config.setApiTokenEncrypted(secretCipher.encrypt("wp-token"));
        config = configRepository.save(config);

        workflow = new BuildWorkflow();
        workflow.setBuildServerConfig(config);
        workflow.setName("Nightly");
        workflow.setRepoRef("42");
        workflow.setDefaultRef("main");
        workflow = workflowRepository.save(workflow);

        ProjectBuildWorkflow assignment = new ProjectBuildWorkflow();
        assignment.setProjectId(project.getId());
        assignment.setWorkflow(workflow);
        assignmentRepository.save(assignment);
    }

    @AfterEach
    void tearDown() {
        stub.stop(0);
        // Not @Transactional, so committed fixtures must be removed by hand. Deleting the config
        // cascades workflows and assignments; deleting the project cascades its pipeline runs.
        configRepository.deleteById(config.getId());
        projectRepository.deleteById(project.getId());
        userRepository.deleteById(testerUser.getId());
    }

    @Test
    void trigger_outsideATransaction_createsRunAndSendsCorrelationVariables() throws Exception {
        mockMvc.perform(post("/api/projects/" + project.getId() + "/workflows/"
                        + workflow.getId() + "/trigger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parameters\":{\"ENVIRONMENT\":\"staging\"}}")
                        .with(user(testerUser.getId().toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.externalRunId").value("7"))
                .andExpect(jsonPath("$.workflowName").value("Nightly"));

        // The stub received the tester's override and the injected correlation variables.
        assertThat(triggerBody.get()).contains("\"branch\":\"main\"");
        assertThat(triggerBody.get()).contains("ENVIRONMENT");
        assertThat(triggerBody.get()).contains("TM_PIPELINE_RUN_ID");
        assertThat(triggerBody.get()).contains("TM_PROJECT_KEY");

        PipelineRun run = pipelineRunRepository
                .findByProjectIdOrderByCreatedAtDesc(project.getId(),
                        org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().getFirst();
        assertThat(run.getStatus()).isEqualTo(PipelineRunStatus.PENDING);
        assertThat(run.getExternalRunId()).isEqualTo("7");
        assertThat(run.getParameters()).contains("TM_PIPELINE_RUN_ID");
    }
}
