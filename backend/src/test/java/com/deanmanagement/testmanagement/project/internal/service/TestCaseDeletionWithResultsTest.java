package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.testCase.BulkDeleteRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testSuite.CreateTestSuiteRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.shared.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A case with results used to fail its delete with the database's bare "conflicts with existing
 * data", which the UI swallowed (TM01). The refusal now names the runs holding the results. A case
 * that never ran must still go, even from inside a suite.
 */
@SpringBootTest
@ActiveProfiles("dev")
class TestCaseDeletionWithResultsTest {

    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TestCaseService testCaseService;
    @Autowired
    private TestRunService testRunService;
    @Autowired
    private TestSuiteService testSuiteService;

    private UUID projectId;
    private UUID testCaseId;
    private String runKey;

    @BeforeEach
    void executedCase() {
        Project project = new Project();
        project.setName("Fall mit Ergebnis");
        project.setKey("TCD" + Integer.toHexString(new java.util.Random().nextInt(0xFFFF)));
        projectId = projectRepository.save(project).getId();
        testCaseId = testCaseService.create(projectId,
                new CreateTestCaseRequest("Ein Fall", null, null, Priority.MEDIUM,
                        TestCaseStatus.ACTIVE, null, null, null), null).id();
        runKey = testRunService.create(projectId,
                new CreateTestRunRequest("Ein Lauf", "Produktion", Set.of(testCaseId), null, null),
                null).key();
    }

    @Test
    void deletingACaseWithResultsNamesTheRunsHoldingThem() {
        assertThatThrownBy(() -> testCaseService.delete(projectId, testCaseId, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(runKey);
    }

    @Test
    void bulkDeletingCasesWithResultsNamesTheRunsHoldingThem() {
        assertThatThrownBy(() -> testCaseService.bulkDelete(projectId,
                new BulkDeleteRequest(Set.of(testCaseId)), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(runKey);
    }

    @Test
    void aCaseInASuiteCanBeDeleted() {
        UUID unexecuted = testCaseService.create(projectId,
                new CreateTestCaseRequest("Nie ausgeführt", null, null, Priority.MEDIUM,
                        TestCaseStatus.ACTIVE, null, null, null), null).id();
        UUID suiteId = testSuiteService.create(projectId,
                new CreateTestSuiteRequest("Suite", null, Set.of(unexecuted)), null).id();

        testCaseService.delete(projectId, unexecuted, null);

        assertThat(testSuiteService.findById(projectId, suiteId).testCases()).isEmpty();
    }
}
