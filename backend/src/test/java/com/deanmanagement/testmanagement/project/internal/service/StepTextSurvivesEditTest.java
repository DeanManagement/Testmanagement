package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.StepResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestCaseRequest;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Replacing a case's steps while it sits in an open run used to blank the run's steps: the old step
 * rows are deleted and the result's link to them goes null (TM25). Not {@code @Transactional}, so
 * every call commits and the run is read back from the database, as the UI would.
 */
@SpringBootTest
@ActiveProfiles("dev")
class StepTextSurvivesEditTest {

    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TestCaseService testCaseService;
    @Autowired
    private TestRunService testRunService;

    @Test
    void anOpenRunKeepsTheStepTextAfterTheCaseStepsAreReplaced() {
        Project project = new Project();
        project.setName("Schritte ersetzt");
        project.setKey("STE" + Integer.toHexString(new java.util.Random().nextInt(0xFFFF)));
        UUID projectId = projectRepository.save(project).getId();
        UUID caseId = testCaseService.create(projectId, new CreateTestCaseRequest("Ein Fall", null, null,
                Priority.MEDIUM, TestCaseStatus.ACTIVE, null,
                List.of(new TestStepRequest("Anmelden", "Startseite erscheint", "user=a")), null), null).id();
        UUID runId = testRunService.create(projectId,
                new CreateTestRunRequest("Ein Lauf", "Produktion", Set.of(caseId), null, null), null).id();

        testCaseService.update(projectId, caseId, new UpdateTestCaseRequest(null, null, null, null, null, null,
                List.of(new TestStepRequest("Neu anmelden", "Neue Startseite", null))), null);

        StepResultResponse step = testRunService.findById(projectId, runId)
                .results().getFirst().stepResults().getFirst();
        assertThat(step.action()).isEqualTo("Anmelden");
        assertThat(step.expectedResult()).isEqualTo("Startseite erscheint");
        assertThat(step.testData()).isEqualTo("user=a");
    }
}
