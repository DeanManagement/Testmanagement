package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunSummaryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportResponse;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.CreateBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.dto.filter.TestRunListFilter;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CloneTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectEnvironment;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Every way a run or bug gets an environment resolves it against the catalogue (PRD-032 §3.2). */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class EnvironmentWritePathsTest {

    @Autowired
    private TestRunService testRunService;
    @Autowired
    private BugReportService bugReportService;
    @Autowired
    private ProjectEnvironmentService environmentService;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TestRunRepository testRunRepository;

    private UUID projectId;
    private ProjectEnvironment staging;

    @BeforeEach
    void setUp() {
        Project project = new Project();
        project.setName("Write paths");
        project.setKey("WP");
        project.setBugReportsEnabled(true);
        projectId = projectRepository.save(project).getId();
        staging = environmentService.resolve(projectId, null, "staging");
    }

    private TestRunResponse createRun(String environment) {
        return testRunService.create(projectId, new CreateTestRunRequest("Run", environment, null, null, null), null);
    }

    private UUID environmentIdOf(UUID runId) {
        ProjectEnvironment environment = testRunRepository.findById(runId).orElseThrow().getProjectEnvironment();
        return environment != null ? environment.getId() : null;
    }

    @Test
    void createResolvesANameToTheCanonicalEntry() {
        TestRunResponse run = createRun(" STAGING ");

        assertThat(run.environment()).isEqualTo("staging");
        assertThat(environmentIdOf(run.id())).isEqualTo(staging.getId());
    }

    @Test
    void createWithAnIdUsesIt() {
        TestRunResponse run = testRunService.create(projectId,
                new CreateTestRunRequest("Run", null, null, null, null, staging.getId(), null), null);

        assertThat(run.environment()).isEqualTo("staging");
    }

    @Test
    void updateWithAnEmptyNameClearsBothColumns() {
        TestRunResponse run = createRun("staging");

        TestRunResponse updated = testRunService.update(projectId, run.id(),
                new UpdateTestRunRequest(null, "", null, null, null), null);

        assertThat(updated.environment()).isNull();
        assertThat(environmentIdOf(run.id())).isNull();
    }

    @Test
    void updateWithoutEnvironmentLeavesItAlone() {
        TestRunResponse run = createRun("staging");

        TestRunResponse updated = testRunService.update(projectId, run.id(),
                new UpdateTestRunRequest("Renamed", null, null, null, null), null);

        assertThat(updated.environment()).isEqualTo("staging");
    }

    @Test
    void cloneKeepsTheSourceEnvironmentByDefault() {
        TestRunResponse run = createRun("staging");

        TestRunResponse clone = testRunService.cloneRun(projectId, run.id(), new CloneTestRunRequest("Again", null), null);

        assertThat(environmentIdOf(clone.id())).isEqualTo(staging.getId());
    }

    @Test
    void cloneCanMoveToAnotherEnvironment() {
        TestRunResponse run = createRun("staging");

        TestRunResponse clone = testRunService.cloneRun(projectId, run.id(),
                new CloneTestRunRequest("Again", "production"), null);

        assertThat(clone.environment()).isEqualTo("production");
    }

    @Test
    void runListFiltersByEnvironment() {
        createRun("staging");
        createRun("production");

        var page = testRunService.findByProject(projectId,
                new TestRunListFilter(null, null, null, null, null, staging.getId()), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(TestRunSummaryResponse::environment).containsExactly("staging");
    }

    @Test
    void bugReportsResolveNamesAndFilterByEnvironment() {
        BugReportResponse bug = bugReportService.create(projectId, new CreateBugReportRequest("Broken", null, null,
                null, null, Priority.HIGH, "Staging", null, null, null, null), null);
        bugReportService.create(projectId, new CreateBugReportRequest("Elsewhere", null, null,
                null, null, Priority.LOW, "production", null, null, null, null), null);

        assertThat(bug.environment()).isEqualTo("staging");
        assertThat(bugReportService.findByProjectAndEnvironment(projectId, staging.getId()))
                .extracting(BugReportResponse::title).containsExactly("Broken");
    }
}
