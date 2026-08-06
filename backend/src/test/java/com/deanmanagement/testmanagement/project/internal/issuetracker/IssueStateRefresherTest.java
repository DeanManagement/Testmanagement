package com.deanmanagement.testmanagement.project.internal.issuetracker;

import com.deanmanagement.testmanagement.project.internal.entity.IssueLink;
import com.deanmanagement.testmanagement.project.internal.entity.IssueState;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.IssueLinkRepository;
import com.deanmanagement.testmanagement.project.internal.repository.IssueTrackerConfigRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The poller's job is as much about restraint as refreshing: it must ignore links on finished runs
 * and stop calling a provider that has started rejecting us.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class IssueStateRefresherTest {

    @Autowired
    private IssueStateRefresher refresher;
    @Autowired
    private IssueTrackerTokenCipher tokenCipher;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TestCaseRepository testCaseRepository;
    @Autowired
    private TestRunRepository testRunRepository;
    @Autowired
    private TestResultRepository testResultRepository;
    @Autowired
    private IssueLinkRepository issueLinkRepository;
    @Autowired
    private IssueTrackerConfigRepository configRepository;

    private HttpServer server;
    private final AtomicInteger responseCode = new AtomicInteger(200);
    private final AtomicInteger callCount = new AtomicInteger();
    private final AtomicReference<String> issueState = new AtomicReference<>("closed");

    private Project project;
    private IssueTrackerConfig config;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            callCount.incrementAndGet();
            byte[] body = ("{\"iid\": 1, \"title\": \"Issue\", \"state\": \"" + issueState.get()
                    + "\", \"web_url\": \"https://gitlab.test/i/1\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseCode.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        project = new Project();
        project.setName("Polled");
        project.setKey("POLL");
        project = projectRepository.save(project);

        config = new IssueTrackerConfig();
        config.setProjectId(project.getId());
        config.setProvider(IssueTrackerProviderType.GITLAB);
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        config.setProjectRef("group/project");
        config.setApiTokenEncrypted(tokenCipher.encrypt("not-a-real-token-fixture"));
        config.setActive(true);
        config = configRepository.save(config);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private UUID linkOnRunWithStatus(TestRunStatus status, String key) {
        TestCase testCase = new TestCase();
        testCase.setProject(project);
        testCase.setKey(key + "-C");
        testCase.setTitle("case");
        testCase.setPriority(Priority.MEDIUM);
        testCase.setStatus(TestCaseStatus.ACTIVE);
        testCase = testCaseRepository.save(testCase);

        TestRun run = new TestRun();
        run.setProject(project);
        run.setKey(key);
        run.setName("run");
        run.setStatus(status);
        run = testRunRepository.save(run);

        TestResult result = new TestResult();
        result.setTestRun(run);
        result.setTestCase(testCase);
        result.setStatus(TestResultStatus.FAILED);
        result = testResultRepository.save(result);

        IssueLink link = new IssueLink();
        link.setTestResultId(result.getId());
        link.setProvider(IssueTrackerProviderType.GITLAB);
        link.setExternalId("group/project#1");
        link.setUrl("https://gitlab.test/i/1");
        link.setState(IssueState.OPEN);
        return issueLinkRepository.save(link).getId();
    }

    @Test
    void refreshesLinksOnRunsThatAreStillActive() {
        UUID linkId = linkOnRunWithStatus(TestRunStatus.IN_PROGRESS, "POLL-R1");

        int refreshed = refresher.refreshProject(config);

        assertThat(refreshed).isEqualTo(1);
        assertThat(issueLinkRepository.findById(linkId).orElseThrow().getState()).isEqualTo(IssueState.CLOSED);
        assertThat(issueLinkRepository.findById(linkId).orElseThrow().getStateCheckedAt()).isNotNull();
    }

    @Test
    void ignoresLinksOnCompletedRuns() {
        UUID linkId = linkOnRunWithStatus(TestRunStatus.COMPLETED, "POLL-R2");

        int refreshed = refresher.refreshProject(config);

        // A finished run's defect state is history; polling it would spend API budget for nothing.
        assertThat(refreshed).isZero();
        assertThat(callCount.get()).isZero();
        assertThat(issueLinkRepository.findById(linkId).orElseThrow().getState()).isEqualTo(IssueState.OPEN);
    }

    @Test
    void ignoresLinksOnAbortedRuns() {
        linkOnRunWithStatus(TestRunStatus.ABORTED, "POLL-R3");

        assertThat(refresher.refreshProject(config)).isZero();
        assertThat(callCount.get()).isZero();
    }

    @Test
    void stopsTheBatchOnAuthFailureRatherThanRetryingEveryLink() {
        linkOnRunWithStatus(TestRunStatus.IN_PROGRESS, "POLL-R4");
        linkOnRunWithStatus(TestRunStatus.IN_PROGRESS, "POLL-R5");
        linkOnRunWithStatus(TestRunStatus.IN_PROGRESS, "POLL-R6");
        responseCode.set(401);

        int refreshed = refresher.refreshProject(config);

        assertThat(refreshed).isZero();
        // One call, not three: hammering a tracker that just rejected our token is how you get
        // rate-limited or locked out.
        assertThat(callCount.get()).isEqualTo(1);
        assertThat(configRepository.findById(config.getId()).orElseThrow().getLastError())
                .contains("rejected the configured access token");
    }

    @Test
    void doesNothingWhenThereAreNoLinks() {
        assertThat(refresher.refreshProject(config)).isZero();
        assertThat(callCount.get()).isZero();
    }
}
