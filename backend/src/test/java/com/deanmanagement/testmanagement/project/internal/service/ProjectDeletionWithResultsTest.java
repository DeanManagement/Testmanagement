package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Deleting a project must also remove its executions.
 *
 * <p>Found by accident: a test that created a run with results could no longer clean up after
 * itself. {@code Project} cascades to both {@code testCases} and {@code testRuns}, but
 * {@code test_results} points at {@code test_cases} with a NOT NULL foreign key that nothing maps,
 * so Hibernate has no way to know the results must go before the cases they reference.
 *
 * <p>The user-visible symptom is a failed delete on any project that has ever been executed —
 * which is every project that has been used for its purpose.
 */
@SpringBootTest
@ActiveProfiles("dev")
class ProjectDeletionWithResultsTest {

    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private TestCaseService testCaseService;
    @Autowired
    private TestRunService testRunService;

    @Test
    void aProjectWithExecutedTestCasesCanBeDeleted() {
        Project project = new Project();
        project.setName("Wird gelöscht");
        project.setKey("DEL" + Integer.toHexString(new java.util.Random().nextInt(0xFFFF)));
        UUID projectId = projectRepository.save(project).getId();

        UUID testCaseId = testCaseService.create(projectId,
                new CreateTestCaseRequest("Ein Fall", null, null, Priority.MEDIUM,
                        TestCaseStatus.ACTIVE, null, null, null), null).id();
        testRunService.create(projectId,
                new CreateTestRunRequest("Ein Lauf", "Produktion", Set.of(testCaseId), null, null),
                null);

        assertThatCode(() -> projectService.delete(projectId, null))
                .as("a project that has been executed must still be deletable")
                .doesNotThrowAnyException();

        assertThat(projectRepository.findById(projectId)).isEmpty();
    }
}
