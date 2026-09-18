package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.environment.CreateEnvironmentRequest;
import com.deanmanagement.testmanagement.project.internal.dto.environment.EnvironmentResponse;
import com.deanmanagement.testmanagement.project.internal.dto.environment.UpdateEnvironmentRequest;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectEnvironment;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.AuditEntryRepository;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectEnvironmentRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.shared.exception.ConflictException;
import com.deanmanagement.testmanagement.shared.exception.DuplicateKeyException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ProjectEnvironmentServiceTest {

    @Autowired
    private ProjectEnvironmentService service;
    @Autowired
    private ProjectEnvironmentRepository environmentRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TestRunRepository testRunRepository;
    @Autowired
    private BugReportRepository bugReportRepository;
    @Autowired
    private AuditEntryRepository auditEntryRepository;
    @Autowired
    private EntityManager entityManager;

    private UUID projectId;
    private int runNumber;

    @BeforeEach
    void setUp() {
        projectId = newProject("ENV");
    }

    private UUID newProject(String key) {
        Project project = new Project();
        project.setName("Environments " + key);
        project.setKey(key);
        return projectRepository.save(project).getId();
    }

    private ProjectEnvironment environment(String name) {
        return service.resolve(projectId, null, name);
    }

    private UUID runIn(ProjectEnvironment environment) {
        TestRun run = new TestRun();
        run.setProject(projectRepository.getReferenceById(projectId));
        run.setName("Run");
        run.setKey("ENV-Run-" + (++runNumber));
        run.setStatus(TestRunStatus.PLANNED);
        run.assignEnvironment(environment);
        return testRunRepository.save(run).getId();
    }

    private UUID bugIn(ProjectEnvironment environment) {
        BugReport bug = new BugReport();
        bug.setProject(projectRepository.getReferenceById(projectId));
        bug.setTitle("Bug");
        bug.setPriority(Priority.MEDIUM);
        bug.setStatus(BugReportStatus.OPEN);
        bug.assignEnvironment(environment);
        return bugReportRepository.save(bug).getId();
    }

    /** Bulk updates bypass the persistence context, so re-read from the database. */
    private TestRun reloadRun(UUID id) {
        entityManager.flush();
        entityManager.clear();
        return testRunRepository.findById(id).orElseThrow();
    }

    @Nested
    class Resolve {

        @Test
        void matchesIgnoringCaseAndSurroundingWhitespace() {
            ProjectEnvironment staging = environment("Staging");

            assertThat(environment("  staging ").getId()).isEqualTo(staging.getId());
            assertThat(environmentRepository.findByProjectIdOrderBySortOrderAscNameAsc(projectId)).hasSize(1);
        }

        @Test
        void registersAnUnknownNameAndAuditsIt() {
            ProjectEnvironment created = environment(" QA ");

            assertThat(created.getName()).isEqualTo("QA");
            assertThat(auditEntryRepository.findAll()).anyMatch(entry ->
                    entry.getEntityType() == AuditEntityType.ENVIRONMENT && created.getId().equals(entry.getEntityId()));
        }

        @Test
        void returnsNullForBlankOrMissingNames() {
            assertThat(service.resolve(projectId, null, "  ")).isNull();
            assertThat(service.resolve(projectId, null, null)).isNull();
        }

        @Test
        void unarchivesAnEnvironmentThatIsUsedAgain() {
            ProjectEnvironment old = environment("legacy");
            service.update(projectId, old.getId(), new UpdateEnvironmentRequest(null, null, null, true), null);

            assertThat(environment("LEGACY").isArchived()).isFalse();
        }

        @Test
        void refusesAnIdFromAnotherProject() {
            UUID foreignProject = newProject("OTHER");
            ProjectEnvironment foreign = service.resolve(foreignProject, null, "staging");

            assertThatThrownBy(() -> service.resolve(projectId, foreign.getId(), null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void prefersTheIdOverTheName() {
            ProjectEnvironment staging = environment("staging");

            assertThat(service.resolve(projectId, staging.getId(), "production").getId()).isEqualTo(staging.getId());
        }
    }

    @Nested
    class Curate {

        @Test
        void renamePropagatesToRunsAndBugs() {
            ProjectEnvironment stage = environment("stage");
            UUID runId = runIn(stage);
            UUID bugId = bugIn(stage);

            service.update(projectId, stage.getId(), new UpdateEnvironmentRequest("Staging", null, null, null), null);

            assertThat(reloadRun(runId).getEnvironment()).isEqualTo("Staging");
            assertThat(bugReportRepository.findById(bugId).orElseThrow().getEnvironment()).isEqualTo("Staging");
        }

        @Test
        void renameOntoAnExistingNameIsAConflict() {
            environment("staging");
            ProjectEnvironment stage = environment("stage");

            assertThatThrownBy(() -> service.update(projectId, stage.getId(),
                    new UpdateEnvironmentRequest("STAGING", null, null, null), null))
                    .isInstanceOf(DuplicateKeyException.class);
        }

        @Test
        void createRejectsADuplicateName() {
            environment("staging");

            assertThatThrownBy(() -> service.create(projectId, new CreateEnvironmentRequest(" Staging", null), null))
                    .isInstanceOf(DuplicateKeyException.class);
        }

        @Test
        void mergeRepointsEverythingAndDeletesTheSource() {
            ProjectEnvironment stg = environment("stg");
            ProjectEnvironment staging = environment("staging");
            UUID runId = runIn(stg);
            UUID bugId = bugIn(stg);

            EnvironmentResponse merged = service.merge(projectId, stg.getId(), staging.getId(), null);

            TestRun run = reloadRun(runId);
            assertThat(run.getProjectEnvironment().getId()).isEqualTo(staging.getId());
            assertThat(run.getEnvironment()).isEqualTo("staging");
            assertThat(bugReportRepository.findById(bugId).orElseThrow().getEnvironment()).isEqualTo("staging");
            assertThat(environmentRepository.findById(stg.getId())).isEmpty();
            assertThat(merged.runCount()).isEqualTo(1);
            assertThat(merged.bugCount()).isEqualTo(1);
        }

        @Test
        void deletingAnEnvironmentInUseIsAConflict() {
            ProjectEnvironment staging = environment("staging");
            runIn(staging);

            assertThatThrownBy(() -> service.delete(projectId, staging.getId(), null))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("archive");
        }

        @Test
        void deletingAnUnusedEnvironmentRemovesIt() {
            ProjectEnvironment unused = environment("unused");

            service.delete(projectId, unused.getId(), null);

            assertThat(environmentRepository.findById(unused.getId())).isEmpty();
        }

        @Test
        void listHidesArchivedUnlessAskedAndCountsUsage() {
            ProjectEnvironment staging = environment("staging");
            ProjectEnvironment old = environment("old");
            runIn(staging);
            service.update(projectId, old.getId(), new UpdateEnvironmentRequest(null, null, null, true), null);

            assertThat(service.list(projectId, false)).extracting(EnvironmentResponse::name).containsExactly("staging");
            assertThat(service.list(projectId, true)).hasSize(2)
                    .filteredOn(e -> e.name().equals("staging")).extracting(EnvironmentResponse::runCount).containsExactly(1L);
        }
    }
}
