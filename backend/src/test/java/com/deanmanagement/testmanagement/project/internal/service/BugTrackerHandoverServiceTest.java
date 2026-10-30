package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.StubHttpServer;
import com.deanmanagement.testmanagement.project.internal.StubHttpServer.Response;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.ChangeBugStatusRequest;
import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.BugResolution;
import com.deanmanagement.testmanagement.project.internal.entity.IssueLink;
import com.deanmanagement.testmanagement.project.internal.entity.IssueState;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.issuetracker.IssueStateRefresher;
import com.deanmanagement.testmanagement.project.internal.issuetracker.IssueTrackerTokenCipher;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.IssueLinkRepository;
import com.deanmanagement.testmanagement.project.internal.repository.IssueTrackerConfigRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.shared.exception.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TM50: a bug handed over to Azure DevOps, against a stub of the work item API. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class BugTrackerHandoverServiceTest {

    private static final String STATES = "{\"value\":["
            + "{\"name\":\"New\",\"category\":\"Proposed\"},"
            + "{\"name\":\"Active\",\"category\":\"InProgress\"},"
            + "{\"name\":\"Resolved\",\"category\":\"Resolved\"},"
            + "{\"name\":\"Closed\",\"category\":\"Completed\"},"
            + "{\"name\":\"Removed\",\"category\":\"Removed\"}]}";

    @Autowired private BugTrackerHandoverService handoverService;
    @Autowired private BugReportService bugReportService;
    @Autowired private IssueStateRefresher refresher;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private BugReportRepository bugReportRepository;
    @Autowired private IssueLinkRepository issueLinkRepository;
    @Autowired private IssueTrackerConfigRepository configRepository;
    @Autowired private IssueTrackerTokenCipher tokenCipher;

    private final ObjectMapper json = new ObjectMapper();
    private StubHttpServer server;
    private Project project;
    private IssueTrackerConfig config;
    private BugReport bug;

    @BeforeEach
    void setUp() throws IOException {
        server = new StubHttpServer();
        project = new Project();
        project.setName("Handover");
        project.setKey("HND");
        project.setBugReportsEnabled(true);
        project = projectRepository.save(project);
        config = tracker(IssueTrackerProviderType.AZURE_DEVOPS);
        bug = bug("HND-BUG-1");
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private IssueTrackerConfig tracker(IssueTrackerProviderType type) {
        IssueTrackerConfig tracker = new IssueTrackerConfig();
        tracker.setProjectId(project.getId());
        tracker.setProvider(type);
        tracker.setBaseUrl(server.baseUrl() + "/contoso");
        tracker.setProjectRef("Payments");
        tracker.setApiTokenEncrypted(tokenCipher.encrypt("not-a-real-token-fixture"));
        tracker.setActive(true);
        return configRepository.save(tracker);
    }

    private BugReport bug(String key) {
        BugReport created = new BugReport();
        created.setKey(key);
        created.setProject(project);
        created.setTitle("Checkout fails");
        created.setDescription("Paying by card");
        created.setActualBehavior("HTTP 500");
        created.setPriority(Priority.HIGH);
        created.setStatus(BugReportStatus.NEW);
        return bugReportRepository.save(created);
    }

    private static String workItem(int id, String state) {
        return "{\"id\":" + id + ",\"fields\":{\"System.Title\":\"Checkout fails\",\"System.State\":\"" + state
                + "\",\"System.WorkItemType\":\"Bug\",\"System.TeamProject\":\"Payments\"}}";
    }

    private BugReportStatus statusOf(BugReport target) {
        return bugReportRepository.findById(target.getId()).orElseThrow().getStatus();
    }

    @Nested
    class HandingOver {

        @Test
        void filesABugWorkItemWithTheTextsAndPriority() {
            server.respond(Response.json(workItem(41, "New")), Response.json(STATES));

            handoverService.handOver(project.getId(), bug.getKey(), null, null);

            JsonNode operations = json.readTree(server.requests().get(0).body());
            assertThat(server.requests().get(0).pathAndQuery()).startsWith("/contoso/Payments/_apis/wit/workitems/$Bug");
            assertThat(operations.toString()).contains("Checkout fails", "Paying by card", "HTTP 500", "HND-BUG-1");
            assertThat(operations.valueStream()
                    .filter(op -> op.get("path").asString().equals("/fields/Microsoft.VSTS.Common.Priority"))
                    .map(op -> op.get("value").asInt()))
                    .containsExactly(2);
        }

        @Test
        void theBugTakesTheWorkItemsStatus() {
            server.respond(Response.json(workItem(41, "New")), Response.json(STATES));

            var link = handoverService.handOver(project.getId(), bug.getKey(), null, null);

            assertThat(link.externalId()).isEqualTo("41");
            assertThat(link.trackerState()).isEqualTo("New");
            assertThat(statusOf(bug)).isEqualTo(BugReportStatus.OPEN);
        }

        @Test
        void linkingAClosedWorkItemClosesTheBugAsFixed() {
            server.respond(Response.json(workItem(7, "Closed")), Response.json(STATES));

            handoverService.handOver(project.getId(), bug.getKey(), "7", null);

            BugReport closed = bugReportRepository.findById(bug.getId()).orElseThrow();
            assertThat(closed.getStatus()).isEqualTo(BugReportStatus.CLOSED);
            assertThat(closed.getResolution()).isEqualTo(BugResolution.FIXED);
        }

        @Test
        void aBugIsHandedOverOnlyOnce() {
            server.respond(Response.json(workItem(41, "New")), Response.json(STATES));
            handoverService.handOver(project.getId(), bug.getKey(), null, null);

            assertThatThrownBy(() -> handoverService.handOver(project.getId(), bug.getKey(), null, null))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("41");
        }

        @Test
        void twoBugsCannotFollowOneWorkItem() {
            server.respond(Response.json(workItem(41, "New")), Response.json(STATES));
            handoverService.handOver(project.getId(), bug.getKey(), null, null);
            BugReport second = bug("HND-BUG-2");
            server.respond(Response.json(workItem(41, "New")));

            assertThatThrownBy(() -> handoverService.handOver(project.getId(), second.getKey(), "41", null))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("HND-BUG-1");
        }

        @Test
        void anotherTrackerIsRefused() {
            config.setProvider(IssueTrackerProviderType.GITLAB);
            configRepository.save(config);

            assertThatThrownBy(() -> handoverService.handOver(project.getId(), bug.getKey(), null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Azure DevOps only");
        }
    }

    @Nested
    class StatusOwnership {

        @Test
        void theStatusCannotBeChangedHereWhileHandedOver() {
            server.respond(Response.json(workItem(41, "New")), Response.json(STATES));
            handoverService.handOver(project.getId(), bug.getKey(), null, null);

            assertThatThrownBy(() -> bugReportService.changeStatus(project.getId(), bug.getId(),
                    new ChangeBugStatusRequest(BugReportStatus.IN_PROGRESS, "working on it"), null))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("work item 41");
        }

        @Test
        void detachingGivesTheStatusBack() {
            server.respond(Response.json(workItem(41, "New")), Response.json(STATES));
            handoverService.handOver(project.getId(), bug.getKey(), null, null);

            handoverService.detach(project.getId(), bug.getKey(), null);

            bugReportService.changeStatus(project.getId(), bug.getId(),
                    new ChangeBugStatusRequest(BugReportStatus.IN_PROGRESS, "working on it"), null);
            assertThat(statusOf(bug)).isEqualTo(BugReportStatus.IN_PROGRESS);
        }
    }

    @Nested
    class Polling {

        private IssueLink handedOver() {
            server.respond(Response.json(workItem(41, "New")), Response.json(STATES));
            handoverService.handOver(project.getId(), bug.getKey(), null, null);
            IssueLink link = issueLinkRepository.findByBugReportId(bug.getId()).orElseThrow();
            link.setStateCheckedAt(null);
            return issueLinkRepository.save(link);
        }

        @Test
        void theBugFollowsTheWorkItemToResolved() {
            handedOver();
            server.respond(Response.json(workItem(41, "Resolved")));

            refresher.refreshProject(config);

            assertThat(statusOf(bug)).isEqualTo(BugReportStatus.RESOLVED);
        }

        @Test
        void aDeletedWorkItemLeavesTheBugsStatusAlone() {
            IssueLink link = handedOver();
            server.respond(Response.json(404, "{\"message\":\"TF401232: Work item 41 does not exist\"}"));

            refresher.refreshProject(config);

            assertThat(issueLinkRepository.findById(link.getId()).orElseThrow().getState()).isEqualTo(IssueState.UNKNOWN);
            assertThat(statusOf(bug)).isEqualTo(BugReportStatus.OPEN);
        }
    }

    @Nested
    class StateMapping {

        @Test
        void removedClosesAsWontFix() {
            assertThat(BugTrackerHandoverService.targetFor("Removed", null))
                    .isEqualTo(new BugTrackerHandoverService.Target(BugReportStatus.CLOSED, BugResolution.WONT_FIX));
        }

        @Test
        void completedKeepsAResolutionTheBugAlreadyHad() {
            assertThat(BugTrackerHandoverService.targetFor("Completed", BugResolution.CANNOT_REPRODUCE).resolution())
                    .isEqualTo(BugResolution.CANNOT_REPRODUCE);
        }

        @Test
        void anUnknownCategoryLeavesTheBugAlone() {
            assertThat(BugTrackerHandoverService.targetFor("SomethingNew", null)).isNull();
        }

        @Test
        void reopeningClearsTheResolution() {
            assertThat(BugTrackerHandoverService.targetFor("Proposed", BugResolution.FIXED))
                    .isEqualTo(new BugTrackerHandoverService.Target(BugReportStatus.OPEN, null));
        }
    }
}
