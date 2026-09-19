package com.deanmanagement.testmanagement.project.internal.buildserver;

import com.deanmanagement.testmanagement.project.internal.StubHttpServer;
import com.deanmanagement.testmanagement.project.internal.StubHttpServer.Response;
import com.deanmanagement.testmanagement.project.internal.entity.BuildServerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.BuildServerProviderType;
import com.deanmanagement.testmanagement.project.internal.entity.BuildWorkflow;
import com.deanmanagement.testmanagement.project.internal.entity.PipelineRun;
import com.deanmanagement.testmanagement.project.internal.entity.PipelineRunStatus;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.repository.BuildServerConfigRepository;
import com.deanmanagement.testmanagement.project.internal.repository.BuildWorkflowRepository;
import com.deanmanagement.testmanagement.project.internal.repository.PipelineRunRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.project.internal.service.ProjectService;
import com.deanmanagement.testmanagement.shared.crypto.AesGcmCipher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD-026 §3.4: a finished Azure DevOps run whose workflow pulls test results gets a test run of
 * them, once. Not {@code @Transactional}: the puller commits in steps of its own, and a test
 * transaction around it would hide a step that never commits.
 */
@SpringBootTest
@ActiveProfiles("dev")
class PipelineResultPullerApiTest {

    private static final String PUBLISHED = "{\"value\":[{\"outcome\":\"Passed\",\"testCaseTitle\":\"Login works\"},"
            + "{\"outcome\":\"Failed\",\"testCaseTitle\":\"Checkout works\",\"errorMessage\":\"HTTP 500\"}]}";

    @Autowired
    private PipelineResultPuller puller;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private BuildServerConfigRepository configRepository;
    @Autowired
    private BuildWorkflowRepository workflowRepository;
    @Autowired
    private PipelineRunRepository runRepository;
    @Autowired
    private TestRunRepository testRunRepository;
    @Autowired
    private AesGcmCipher cipher;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private StubHttpServer azure;
    private Project project;
    private BuildServerConfig server;
    private BuildWorkflow workflow;

    @BeforeEach
    void setUp() throws IOException {
        azure = new StubHttpServer();
        project = new Project();
        project.setName("Pull");
        project.setKey("PUL" + Integer.toHexString(new java.util.Random().nextInt(0xFFF)).toUpperCase());
        project = projectRepository.save(project);

        server = new BuildServerConfig();
        server.setName("Azure " + UUID.randomUUID());
        server.setProvider(BuildServerProviderType.AZURE_DEVOPS);
        server.setBaseUrl(azure.baseUrl() + "/contoso");
        server.setApiTokenEncrypted(cipher.encrypt("pat"));
        server = configRepository.save(server);

        workflow = new BuildWorkflow();
        workflow.setBuildServerConfig(server);
        workflow.setName("CI");
        workflow.setRepoRef("Payments");
        workflow.setWorkflowRef("42");
        workflow.setPullTestResults(true);
        workflow = workflowRepository.save(workflow);
    }

    @AfterEach
    void tearDown() {
        azure.close();
        projectService.delete(project.getId(), null);
        configRepository.deleteById(server.getId());
    }

    private PipelineRun finishedRun(Instant finishedAt) {
        PipelineRun run = new PipelineRun();
        run.setWorkflow(workflow);
        run.setProjectId(project.getId());
        run.setWorkflowName("CI");
        run.setStatus(PipelineRunStatus.FAILED);
        run.setExternalRunId("17");
        run.setFinishedAt(finishedAt);
        return runRepository.save(run);
    }

    private PipelineRun reload(PipelineRun run) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            PipelineRun loaded = runRepository.findById(run.getId()).orElseThrow();
            if (loaded.getTestRun() != null) {
                loaded.getTestRun().getResults().size();
            }
            return loaded;
        });
    }

    @Test
    void theResultsAzureDevOpsCollectedBecomeATestRunOfThePipelineRun() {
        PipelineRun run = finishedRun(Instant.now());
        azure.respond(Response.json("{\"value\":[{\"id\":501}]}"), Response.json(PUBLISHED));

        assertThat(puller.pullBatch()).isEqualTo(1);

        PipelineRun pulled = reload(run);
        TestRun testRun = pulled.getTestRun();
        assertThat(pulled.getResultsPulledAt()).isNotNull();
        assertThat(testRun.getName()).isEqualTo("CI");
        assertThat(testRun.getResults()).extracting(r -> r.getStatus())
                .containsExactlyInAnyOrder(TestResultStatus.PASSED, TestResultStatus.FAILED);
    }

    @Test
    void aPipelineThatPublishedNothingIsAskedOnceAndGetsNoTestRun() {
        PipelineRun run = finishedRun(Instant.now());
        azure.respond(Response.json("{\"value\":[]}"));

        puller.pullBatch();
        puller.pullBatch();

        assertThat(reload(run).getTestRun()).isNull();
        assertThat(reload(run).getResultsPulledAt()).isNotNull();
        assertThat(azure.requests()).hasSize(1);
    }

    @Test
    void aRefusedPullIsRecordedOnTheRunAndNotRetried() {
        PipelineRun run = finishedRun(Instant.now());
        azure.respond(new Response(203, "text/html", "<html>Sign in</html>"));

        puller.pullBatch();

        assertThat(reload(run).getErrorMessage()).contains("Pulling test results failed")
                .contains("rejected the configured access token");
        assertThat(puller.hasWork()).isFalse();
    }

    @Test
    void resultsThePipelinePushedWinAndNothingIsPulled() {
        PipelineRun run = finishedRun(Instant.now());
        TestRun pushed = new TestRun();
        pushed.setProject(project);
        pushed.setName("Pushed");
        pushed.setKey(project.getKey() + "-Run-99");
        pushed.setStatus(com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus.COMPLETED);
        run.setTestRun(testRunRepository.save(pushed));
        runRepository.save(run);

        assertThat(puller.hasWork()).isFalse();
    }

    @Test
    void switchingPullingOnDoesNotImportOldRuns() {
        finishedRun(Instant.now().minus(PipelineResultPuller.LOOKBACK).minus(Duration.ofHours(1)));

        assertThat(puller.hasWork()).isFalse();
    }

    @Test
    void aWorkflowThatDoesNotPullIsLeftAlone() {
        workflow.setPullTestResults(false);
        workflowRepository.save(workflow);
        finishedRun(Instant.now());

        assertThat(puller.hasWork()).isFalse();
    }
}
