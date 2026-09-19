package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntry;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.AuditEntryRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The run lifecycle (bug report efb94f3f): abort with a reason, reopen from either end state. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class TestRunLifecycleTest {

    @Autowired private TestRunService testRunService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private AuditEntryRepository auditEntryRepository;

    private UUID projectId;
    private UUID runId;

    @BeforeEach
    void setUp() {
        Project project = new Project();
        project.setName("Lifecycle");
        project.setKey("LC" + Integer.toHexString(new java.util.Random().nextInt(0xFFFF)).toUpperCase());
        projectId = projectRepository.save(project).getId();
        runId = testRunService.create(projectId, new CreateTestRunRequest("Run", null, Set.of(), null, null, null,
                null, null), null).id();
    }

    private TestRunResponse set(TestRunStatus status, String reopenReason, String abortReason) {
        return testRunService.update(projectId, runId,
                new UpdateTestRunRequest(null, null, status, reopenReason, null, null, null, abortReason), null);
    }

    private AuditEntry lastAudit() {
        return auditEntryRepository.findAll().stream()
                .filter(e -> runId.equals(e.getEntityId()))
                .max(Comparator.comparing(AuditEntry::getCreatedAt))
                .orElseThrow();
    }

    @Nested
    class Aborting {

        @Test
        void needsAReason() {
            set(TestRunStatus.IN_PROGRESS, null, null);

            assertThatThrownBy(() -> set(TestRunStatus.ABORTED, null, " "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Abort reason");
        }

        @Test
        void keepsTheReasonOnTheRunAndInTheHistory() {
            set(TestRunStatus.IN_PROGRESS, null, null);

            TestRunResponse run = set(TestRunStatus.ABORTED, null, "Staging is down");

            assertThat(run.status()).isEqualTo(TestRunStatus.ABORTED);
            assertThat(run.abortReason()).isEqualTo("Staging is down");
            assertThat(run.endTime()).isNotNull();
            assertThat(lastAudit().getDetails()).isEqualTo("Staging is down");
        }
    }

    @Nested
    class Reopening {

        @Test
        void anAbortedRunReopensWithAReasonLikeACompletedOne() {
            set(TestRunStatus.IN_PROGRESS, null, null);
            set(TestRunStatus.ABORTED, null, "Staging is down");

            TestRunResponse run = set(TestRunStatus.IN_PROGRESS, "Staging is back", null);

            assertThat(run.status()).isEqualTo(TestRunStatus.IN_PROGRESS);
            assertThat(run.endTime()).isNull();
            assertThat(run.abortReason()).isNull();
            assertThat(run.reopenReason()).isEqualTo("Staging is back");
            assertThat(lastAudit().getAction()).isEqualTo(AuditAction.REOPENED);
        }

        @Test
        void needsAReasonFromEitherEndState() {
            set(TestRunStatus.IN_PROGRESS, null, null);
            set(TestRunStatus.ABORTED, null, "Staging is down");

            assertThatThrownBy(() -> set(TestRunStatus.IN_PROGRESS, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Reopen reason");
        }
    }

    @Nested
    class Refused {

        @Test
        void aClosedRunIsNotSwitchedToTheOtherEndState() {
            set(TestRunStatus.COMPLETED, null, null);

            assertThatThrownBy(() -> set(TestRunStatus.ABORTED, null, "Too late"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reopen it first");
        }

        @Test
        void aRunNeverGoesBackToPlanned() {
            set(TestRunStatus.IN_PROGRESS, null, null);

            assertThatThrownBy(() -> set(TestRunStatus.PLANNED, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void settingTheCurrentStatusAgainChangesNothing() {
            set(TestRunStatus.IN_PROGRESS, null, null);
            TestRunResponse completed = set(TestRunStatus.COMPLETED, null, null);

            TestRunResponse again = set(TestRunStatus.COMPLETED, null, null);

            assertThat(again.endTime()).isEqualTo(completed.endTime());
        }
    }
}
