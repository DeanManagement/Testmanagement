package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.myqueue.MyQueueResponse;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.repository.NotificationRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.service.MyQueueService;
import com.deanmanagement.testmanagement.project.internal.service.TestRunService;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TM23: results of a run assigned to testers. Not transactional, so every write here has committed. */
class McpRunAssignmentApiTest extends McpToolApiTestSupport {

    @Autowired private TestRunCaseTools runCaseTools;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private MyQueueService myQueueService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private TestRunService testRunService;

    private User tess;
    private McpDtos.CreatedTestRun run;
    private McpDtos.TestResult first;

    @BeforeEach
    void seed() {
        tess = new User();
        tess.setEmail("tess-" + UUID.randomUUID() + "@test.local");
        tess.setDisplayName("Tess");
        tess.setPasswordHash("x");
        tess = userRepository.save(tess);
        ProjectMember membership = new ProjectMember();
        membership.setUser(tess);
        membership.setProject(project);
        membership.setRole(ProjectRole.TESTER);
        projectMemberRepository.save(membership);

        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login", "Step");
        McpDtos.CreatedTestCase logout = createCase("Logout", "Step");
        run = testRunWriteTools.createTestRun("Run", null, new LinkedHashSet<>(List.of(login.id(), logout.id())),
                null, null);
        first = testRunReadTools.getTestRun(run.key(), null).results().getFirst();
    }

    @Test
    void anAssignedResultShowsItsAssignee() {
        McpDtos.TestRunDetail detail = runCaseTools.assignTestRunCases(run.key(), List.of(first.id()),
                tess.getEmail().toUpperCase());

        assertThat(detail.results()).extracting(McpDtos.TestResult::assignee).first().isEqualTo("Tess");
        assertThat(detail.results().get(1).assignee()).startsWith("API key:");
    }

    @Test
    void unassigningFallsBackToTheRunsExecutor() {
        runCaseTools.assignTestRunCases(run.key(), List.of(first.id()), tess.getEmail());
        testRunService.setExecutor(project.getId(), run.id(), tess.getId());
        McpDtos.CreatedTestCase other = createCase("Other", "Step");
        runCaseTools.addTestCasesToRun(run.key(), List.of(other.id()));

        McpDtos.TestRunDetail detail = runCaseTools.assignTestRunCases(run.key(), List.of(first.id()), "none");

        assertThat(detail.results()).extracting(McpDtos.TestResult::assignee).containsOnly("Tess");
    }

    @Test
    void aPendingAssignedResultIsInTheAssigneesQueueUntilItIsRecorded() {
        runCaseTools.assignTestRunCases(run.key(), List.of(first.id()), tess.getEmail());
        assertThat(myQueueService.buildFor(tess.getId()).assignedResults())
                .extracting(MyQueueResponse.AssignedResultItem::id).containsExactly(first.id());

        resultRecordingTools.recordTestResult(run.key(), TestResultStatus.PASSED, null, first.id(),
                null, null, null, null, null);

        assertThat(myQueueService.buildFor(tess.getId()).assignedResults()).isEmpty();
    }

    @Test
    void theAssigneeIsNotified() {
        runCaseTools.assignTestRunCases(run.key(), List.of(first.id()), tess.getEmail());

        assertThat(notificationRepository.findAll())
                .anyMatch(n -> n.getUserId().equals(tess.getId()) && n.getEntityId().equals(run.id()));
    }

    @Test
    void aNonMemberIsRefusedAndNothingChanges() {
        assertThatThrownBy(() -> runCaseTools.assignTestRunCases(run.key(), List.of(first.id()), "nobody@test.local"))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("No member");
    }

    @Test
    void aTestCaseIdIsNotAResultId() {
        UUID testCaseId = first.testCaseId();

        assertThatThrownBy(() -> runCaseTools.assignTestRunCases(run.key(), List.of(testCaseId), tess.getEmail()))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("not test case ids");
    }
}
