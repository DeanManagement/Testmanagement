package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.IssueState;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.issuetracker.IssueTrackerTokenCipher;
import com.deanmanagement.testmanagement.project.internal.repository.IssueLinkRepository;
import com.deanmanagement.testmanagement.project.internal.repository.IssueTrackerConfigRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Linking issues to a result, end to end against a stub GitLab. Covers the templated body a tester
 * gets when filing from a failure, the TESTER/VIEWER split, and cross-project isolation.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class IssueLinkApiTest {

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
    @Autowired
    private TestRunRepository testRunRepository;
    @Autowired
    private TestResultRepository testResultRepository;
    @Autowired
    private IssueTrackerConfigRepository configRepository;
    @Autowired
    private IssueLinkRepository issueLinkRepository;
    @Autowired
    private IssueTrackerTokenCipher tokenCipher;

    private HttpServer server;
    private final AtomicInteger responseCode = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("{}");
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    private String admin;
    private String tester;
    private String viewer;
    private UUID projectId;
    private UUID runId;
    private UUID resultId;
    private UUID otherProjectResultId;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseCode.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        User adminUser = saveUser();
        User testerUser = saveUser();
        User viewerUser = saveUser();
        admin = adminUser.getId().toString();
        tester = testerUser.getId().toString();
        viewer = viewerUser.getId().toString();

        Project project = saveProject("Tracked", "TRK");
        projectId = project.getId();
        saveMember(adminUser, project, ProjectRole.ADMIN);
        saveMember(testerUser, project, ProjectRole.TESTER);
        saveMember(viewerUser, project, ProjectRole.VIEWER);

        TestCase testCase = saveTestCase(project, "TRK-1", "Checkout fails on empty cart");
        TestRun run = saveRun(project, "TRK-R1", "Regression round 1");
        runId = run.getId();
        resultId = saveResult(run, testCase, "Reproduced twice on staging").getId();

        Project other = saveProject("Other", "OTH");
        saveMember(testerUser, other, ProjectRole.TESTER);
        otherProjectResultId = saveResult(
                saveRun(other, "OTH-R1", "Other run"),
                saveTestCase(other, "OTH-1", "Unrelated"),
                null).getId();

        saveTrackerConfig(projectId);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    // ---- fixtures ---------------------------------------------------------

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

    private TestCase saveTestCase(Project project, String key, String title) {
        TestCase testCase = new TestCase();
        testCase.setProject(project);
        testCase.setKey(key);
        testCase.setTitle(title);
        testCase.setPriority(Priority.HIGH);
        testCase.setStatus(TestCaseStatus.ACTIVE);
        return testCaseRepository.save(testCase);
    }

    private TestRun saveRun(Project project, String key, String name) {
        TestRun run = new TestRun();
        run.setProject(project);
        run.setKey(key);
        run.setName(name);
        run.setEnvironment("staging");
        run.setStatus(TestRunStatus.IN_PROGRESS);
        return testRunRepository.save(run);
    }

    private TestResult saveResult(TestRun run, TestCase testCase, String comment) {
        TestResult result = new TestResult();
        result.setTestRun(run);
        result.setTestCase(testCase);
        result.setStatus(TestResultStatus.FAILED);
        result.setComment(comment);
        return testResultRepository.save(result);
    }

    private void saveTrackerConfig(UUID project) {
        IssueTrackerConfig config = new IssueTrackerConfig();
        config.setProjectId(project);
        config.setProvider(IssueTrackerProviderType.GITLAB);
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        config.setProjectRef("group/project");
        config.setApiTokenEncrypted(tokenCipher.encrypt("not-a-real-token-fixture"));
        config.setActive(true);
        configRepository.save(config);
    }

    private String issuesUrl(UUID result) {
        return "/api/projects/" + projectId + "/test-runs/" + runId + "/results/" + result + "/issues";
    }

    private void stubIssue(int iid, String state) {
        responseBody.set("{\"iid\": " + iid + ", \"title\": \"Checkout bug\", \"state\": \"" + state
                + "\", \"web_url\": \"https://gitlab.test/i/" + iid + "\"}");
    }

    // ---- tests ------------------------------------------------------------

    @Test
    void testerCanLinkAnExistingIssue() throws Exception {
        stubIssue(42, "opened");

        mockMvc.perform(post(issuesUrl(resultId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"group/project#42\"}")
                        .with(user(tester)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalId").value("group/project#42"))
                .andExpect(jsonPath("$.state").value("OPEN"))
                .andExpect(jsonPath("$.url").value("https://gitlab.test/i/42"));

        assertThat(issueLinkRepository.findByTestResultIdOrderByCreatedAtAsc(resultId)).hasSize(1);
    }

    @Test
    void createdIssueBodyCarriesTheTestContext() throws Exception {
        stubIssue(43, "opened");
        responseCode.set(201);

        mockMvc.perform(post(issuesUrl(resultId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"create\":true}")
                        .with(user(tester)))
                .andExpect(status().isCreated());

        String sent = lastRequestBody.get();
        assertThat(sent).contains("TRK-1");
        assertThat(sent).contains("TRK-R1");
        assertThat(sent).contains("FAILED");
        assertThat(sent).contains("staging");
        assertThat(sent).contains("Reproduced twice on staging");
        // Title defaults to the test case so the issue is recognisable in the tracker's list view.
        assertThat(sent).contains("[TRK-1] Checkout fails on empty cart");
    }

    @Test
    void relinkingTheSameIssueUpdatesRatherThanDuplicates() throws Exception {
        stubIssue(42, "opened");
        mockMvc.perform(post(issuesUrl(resultId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"group/project#42\"}")
                        .with(user(tester)))
                .andExpect(status().isCreated());

        stubIssue(42, "closed");
        mockMvc.perform(post(issuesUrl(resultId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"group/project#42\"}")
                        .with(user(tester)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("CLOSED"));

        assertThat(issueLinkRepository.findByTestResultIdOrderByCreatedAtAsc(resultId)).hasSize(1);
    }

    @Test
    void viewerCanReadLinksButNotCreateThem() throws Exception {
        stubIssue(42, "opened");
        mockMvc.perform(post(issuesUrl(resultId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"group/project#42\"}")
                        .with(user(tester)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(issuesUrl(resultId)).with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(post(issuesUrl(resultId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"group/project#99\"}")
                        .with(user(viewer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void resultFromAnotherProjectIsNotReachable() throws Exception {
        // The tester is a member of both projects, so only the result-to-project check can stop this.
        mockMvc.perform(get(issuesUrl(otherProjectResultId)).with(user(tester)))
                .andExpect(status().isNotFound());
    }

    @Test
    void trackerFailureSurfacesAsBadGateway() throws Exception {
        responseCode.set(500);

        mockMvc.perform(post(issuesUrl(resultId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"group/project#42\"}")
                        .with(user(tester)))
                .andExpect(status().isBadGateway());
    }

    @Test
    void authFailureIsRecordedOnTheConfigForTheSettingsUi() throws Exception {
        responseCode.set(401);

        mockMvc.perform(post(issuesUrl(resultId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"group/project#42\"}")
                        .with(user(tester)))
                .andExpect(status().isBadGateway());

        IssueTrackerConfig config = configRepository.findByProjectId(projectId).orElseThrow();
        assertThat(config.getLastError()).contains("rejected the configured access token");
        assertThat(config.getLastErrorAt()).isNotNull();
    }

    @Test
    void linkingWithoutAReferenceOrCreateFlagIsRejected() throws Exception {
        mockMvc.perform(post(issuesUrl(resultId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user(tester)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unlinkRemovesTheLink() throws Exception {
        stubIssue(42, "opened");
        String created = mockMvc.perform(post(issuesUrl(resultId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"group/project#42\"}")
                        .with(user(tester)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String linkId = created.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(delete(issuesUrl(resultId) + "/" + linkId).with(user(tester)))
                .andExpect(status().isNoContent());

        assertThat(issueLinkRepository.findByTestResultIdOrderByCreatedAtAsc(resultId)).isEmpty();
    }

    @Test
    void refreshPicksUpAStateChange() throws Exception {
        stubIssue(42, "opened");
        mockMvc.perform(post(issuesUrl(resultId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"group/project#42\"}")
                        .with(user(tester)))
                .andExpect(status().isCreated());

        stubIssue(42, "closed");
        mockMvc.perform(post(issuesUrl(resultId) + "/refresh").with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].state").value("CLOSED"));
    }

    @Test
    void refreshKeepsCachedStateWhenTheTrackerIsDown() throws Exception {
        stubIssue(42, "opened");
        mockMvc.perform(post(issuesUrl(resultId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"group/project#42\"}")
                        .with(user(tester)))
                .andExpect(status().isCreated());

        responseCode.set(503);

        // A stale pill beats an error page on the result view.
        mockMvc.perform(post(issuesUrl(resultId) + "/refresh").with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].state").value("OPEN"));

        assertThat(issueLinkRepository.findByTestResultIdOrderByCreatedAtAsc(resultId).getFirst().getState())
                .isEqualTo(IssueState.OPEN);
    }
}
