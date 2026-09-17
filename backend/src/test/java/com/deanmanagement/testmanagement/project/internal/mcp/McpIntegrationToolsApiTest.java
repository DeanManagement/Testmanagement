package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.BuildServerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.BuildServerProviderType;
import com.deanmanagement.testmanagement.project.internal.entity.BuildWorkflow;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectBuildWorkflow;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.issuetracker.IssueTrackerTokenCipher;
import com.deanmanagement.testmanagement.project.internal.repository.BuildServerConfigRepository;
import com.deanmanagement.testmanagement.project.internal.repository.BuildWorkflowRepository;
import com.deanmanagement.testmanagement.project.internal.repository.IssueTrackerConfigRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectBuildWorkflowRepository;
import com.deanmanagement.testmanagement.shared.crypto.AesGcmCipher;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The tools that reach outside the application — a build server and an issue tracker, both played
 * by one local stub that answers every request with {@link #stubResponse} and keeps the last
 * request body, the same fake {@code PipelineTriggerE2eTest} and {@code IssueLinkApiTest} use.
 */
class McpIntegrationToolsApiTest extends McpToolApiTestSupport {

    @Autowired
    private PipelineTools pipelineTools;
    @Autowired
    private IssueLinkTools issueLinkTools;
    @Autowired
    private BuildServerConfigRepository buildServerConfigRepository;
    @Autowired
    private BuildWorkflowRepository workflowRepository;
    @Autowired
    private ProjectBuildWorkflowRepository assignmentRepository;
    @Autowired
    private IssueTrackerConfigRepository trackerConfigRepository;
    @Autowired
    private AesGcmCipher secretCipher;
    @Autowired
    private IssueTrackerTokenCipher tokenCipher;

    private final AtomicReference<String> stubResponse = new AtomicReference<>("{}");
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private HttpServer stub;
    private BuildServerConfig buildServer;

    @BeforeEach
    void startStub() throws IOException {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            byte[] body = stubResponse.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        stub.start();
    }

    @AfterEach
    void stopStub() {
        stub.stop(0);
        // Instance-level, so deleting the project does not take it along; this cascades the
        // workflow and its assignment.
        if (buildServer != null) {
            buildServerConfigRepository.deleteById(buildServer.getId());
        }
        trackerConfigRepository.findAll().stream()
                .filter(config -> config.getProjectId().equals(project.getId()))
                .forEach(trackerConfigRepository::delete);
    }

    private String stubUrl() {
        return "http://127.0.0.1:" + stub.getAddress().getPort();
    }

    private UUID assignWorkflow(UUID projectId) {
        buildServer = new BuildServerConfig();
        buildServer.setName("Stub Woodpecker " + UUID.randomUUID());
        buildServer.setProvider(BuildServerProviderType.WOODPECKER);
        buildServer.setBaseUrl(stubUrl());
        buildServer.setApiTokenEncrypted(secretCipher.encrypt("wp-token"));
        buildServer = buildServerConfigRepository.save(buildServer);

        BuildWorkflow workflow = new BuildWorkflow();
        workflow.setBuildServerConfig(buildServer);
        workflow.setName("Nightly");
        workflow.setRepoRef("42");
        workflow.setDefaultRef("main");
        workflow = workflowRepository.save(workflow);

        ProjectBuildWorkflow assignment = new ProjectBuildWorkflow();
        assignment.setProjectId(projectId);
        assignment.setWorkflow(workflow);
        assignmentRepository.save(assignment);
        return workflow.getId();
    }

    private void configureTracker() {
        IssueTrackerConfig config = new IssueTrackerConfig();
        config.setProjectId(project.getId());
        config.setProvider(IssueTrackerProviderType.GITLAB);
        config.setBaseUrl(stubUrl());
        config.setProjectRef("group/project");
        config.setApiTokenEncrypted(tokenCipher.encrypt("not-a-real-token-fixture"));
        config.setActive(true);
        trackerConfigRepository.save(config);
    }

    private UUID aResult() {
        McpDtos.CreatedTestRun run = runOf(createCase("Checkout fails on empty cart"));
        return testRunReadTools.getTestRun(run.key(), null).results().getFirst().id();
    }

    // --- pipelines -------------------------------------------------------------------------

    @Test
    void aProjectWithNoAssignedWorkflowListsNone() {
        authenticateAs(project, ProjectRole.VIEWER);

        assertThat(pipelineTools.listPipelineWorkflows().total()).isZero();
        assertThat(pipelineTools.listPipelineRuns(null, null).totalElements()).isZero();
    }

    @Test
    void triggeringSendsTheOverridesAndTheRunCanBeReadBack() {
        UUID workflowId = assignWorkflow(project.getId());
        stubResponse.set("{\"number\": 7, \"status\": \"pending\"}");
        authenticateAs(project, ProjectRole.TESTER);

        McpDtos.PipelineRun triggered = pipelineTools.triggerPipeline(workflowId, null,
                Map.of("ENVIRONMENT", "staging"));

        assertThat(triggered.status()).isEqualTo("PENDING");
        assertThat(triggered.triggeredRef()).isEqualTo("main");
        assertThat(lastRequestBody.get()).contains("ENVIRONMENT").contains("TM_PIPELINE_RUN_ID");
        assertThat(pipelineTools.getPipelineRun(triggered.id()).workflowName()).isEqualTo("Nightly");
        assertThat(pipelineTools.listPipelineWorkflows().workflows())
                .extracting(McpDtos.Workflow::name).containsExactly("Nightly");
    }

    /** The assignment is the authorization: another project's workflow is not found. */
    @Test
    void aWorkflowAssignedToAnotherProjectCannotBeTriggered() {
        UUID foreignWorkflow = assignWorkflow(otherProject.getId());
        authenticateAs(project, ProjectRole.TESTER);

        assertThatThrownBy(() -> pipelineTools.triggerPipeline(foreignWorkflow, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(lastRequestBody.get()).isNull();
    }

    @Test
    void aViewerKeyCannotTriggerOrRefresh() {
        UUID workflowId = assignWorkflow(project.getId());
        authenticateAs(project, ProjectRole.VIEWER);

        assertThatThrownBy(() -> pipelineTools.triggerPipeline(workflowId, null, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("TESTER");
        assertThatThrownBy(() -> pipelineTools.refreshPipelineRun(UUID.randomUUID()))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("TESTER");
        assertThat(lastRequestBody.get()).isNull();
    }

    // --- issue links -----------------------------------------------------------------------

    @Test
    void withoutATrackerTheAgentIsPointedAtBugReports() {
        authenticateAs(project, ProjectRole.TESTER);
        UUID resultId = aResult();

        assertThatThrownBy(() -> issueLinkTools.linkIssue(resultId, "42"))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("create_bug_report");
        assertThat(issueLinkTools.listIssueLinks(resultId).total()).isZero();
    }

    @Test
    void anExistingIssueCanBeLinkedAndIsListedAfterwards() {
        configureTracker();
        stubResponse.set("{\"iid\": 42, \"title\": \"Checkout bug\", \"state\": \"opened\", "
                + "\"web_url\": \"https://gitlab.test/i/42\"}");
        authenticateAs(project, ProjectRole.TESTER);
        UUID resultId = aResult();

        McpDtos.IssueLink linked = issueLinkTools.linkIssue(resultId, "group/project#42");

        assertThat(linked.state()).isEqualTo("OPEN");
        assertThat(linked.url()).isEqualTo("https://gitlab.test/i/42");
        assertThat(issueLinkTools.listIssueLinks(resultId).issues())
                .extracting(McpDtos.IssueLink::externalId).containsExactly("group/project#42");
    }

    @Test
    void filingANewIssueWritesItsBodyFromTheResult() {
        configureTracker();
        stubResponse.set("{\"iid\": 43, \"title\": \"Checkout fails on empty cart\", "
                + "\"state\": \"opened\", \"web_url\": \"https://gitlab.test/i/43\"}");
        authenticateAs(project, ProjectRole.TESTER);
        UUID resultId = aResult();

        McpDtos.IssueLink filed = issueLinkTools.createLinkedIssue(resultId, null, null);

        assertThat(filed.externalId()).contains("43");
        assertThat(lastRequestBody.get()).contains("Checkout fails on empty cart");
    }

    @Test
    void linkingNeedsAReferenceAndNeverFilesByAccident() {
        configureTracker();
        authenticateAs(project, ProjectRole.TESTER);
        UUID resultId = aResult();

        assertThatThrownBy(() -> issueLinkTools.linkIssue(resultId, " "))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("create_linked_issue");
        assertThat(lastRequestBody.get()).isNull();
    }

    @Test
    void anotherProjectsResultCannotBeLinked() {
        authenticateAs(otherProject, ProjectRole.TESTER);
        UUID foreignResult = aResult();
        SecurityContextHolder.clearContext();
        configureTracker();
        authenticateAs(project, ProjectRole.TESTER);

        assertThatThrownBy(() -> issueLinkTools.linkIssue(foreignResult, "42"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(lastRequestBody.get()).isNull();
    }

    @Test
    void aViewerKeyCanListIssueLinksButNotCreateThem() {
        authenticateAs(project, ProjectRole.TESTER);
        UUID resultId = aResult();
        SecurityContextHolder.clearContext();
        authenticateAs(project, ProjectRole.VIEWER);

        assertThat(issueLinkTools.listIssueLinks(resultId).total()).isZero();
        assertThatThrownBy(() -> issueLinkTools.createLinkedIssue(resultId, "t", "b"))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("TESTER");
    }
}
