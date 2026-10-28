package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseParameterSet;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseParameterSetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TM21 and TM22: changing which cases an open run holds, and their order, after it was created. */
class McpTestRunCaseToolsApiTest extends McpToolApiTestSupport {

    @Autowired private TestRunCaseTools runCaseTools;
    @Autowired private TestCaseParameterSetRepository parameterSetRepository;

    private McpDtos.CreatedTestCase first;
    private McpDtos.CreatedTestCase second;
    private McpDtos.CreatedTestRun run;

    @BeforeEach
    void seed() {
        authenticateAs(project, ProjectRole.TESTER);
        first = createCase("First", "Step");
        second = createCase("Second", "Step");
        run = testRunWriteTools.createTestRun("Run", null, new java.util.LinkedHashSet<>(List.of(first.id())),
                null, null);
    }

    private List<String> titlesInOrder() {
        return testRunReadTools.getTestRun(run.key(), null).results().stream()
                .map(McpDtos.TestResult::testCaseTitle).toList();
    }

    @Test
    void addedCasesArePendingAndComeLast() {
        McpDtos.TestRunDetail detail = runCaseTools.addTestCasesToRun(run.key(), List.of(second.id()));

        assertThat(detail.results()).extracting(McpDtos.TestResult::testCaseTitle, McpDtos.TestResult::status,
                        McpDtos.TestResult::position)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("First", TestResultStatus.PENDING, 1),
                        org.assertj.core.groups.Tuple.tuple("Second", TestResultStatus.PENDING, 2));
    }

    @Test
    void aCaseAlreadyInTheRunIsNotAddedTwice() {
        runCaseTools.addTestCasesToRun(run.key(), List.of(first.id(), second.id()));

        assertThat(titlesInOrder()).containsExactly("First", "Second");
    }

    @Test
    void anAddedParameterizedCaseGetsOneResultPerSet() {
        for (String name : List.of("min", "max")) {
            TestCaseParameterSet set = new TestCaseParameterSet();
            set.setTestCaseId(second.id());
            set.setName(name);
            set.setValuesJson("{}");
            parameterSetRepository.save(set);
        }

        runCaseTools.addTestCasesToRun(run.key(), List.of(second.id()));

        assertThat(testRunReadTools.getTestRun(run.key(), null).results())
                .extracting(McpDtos.TestResult::parameterSetName).containsExactlyInAnyOrder(null, "min", "max");
    }

    @Test
    void aPendingCaseCanBeRemoved() {
        runCaseTools.addTestCasesToRun(run.key(), List.of(second.id()));

        runCaseTools.removeTestCasesFromRun(run.key(), List.of(first.id()));

        assertThat(titlesInOrder()).containsExactly("Second");
    }

    @Test
    void aCaseWithARecordedResultIsNotRemoved() {
        resultRecordingTools.recordTestResult(run.key(), TestResultStatus.FAILED, first.id(), null,
                null, null, null, null);

        assertThatThrownBy(() -> runCaseTools.removeTestCasesFromRun(run.key(), List.of(first.id())))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining(first.key() + " (FAILED)");
        assertThat(titlesInOrder()).containsExactly("First");
    }

    @Test
    void namedResultsMoveToTheFrontAndTheOrderIsKept() {
        McpDtos.CreatedTestCase third = createCase("Third", "Step");
        runCaseTools.addTestCasesToRun(run.key(), List.of(second.id(), third.id()));
        var thirdResult = testRunReadTools.getTestRun(run.key(), null).results().get(2);

        runCaseTools.reorderTestRunCases(run.key(), List.of(thirdResult.id()));

        assertThat(titlesInOrder()).containsExactly("Third", "First", "Second");
    }

    @Test
    void reorderingRefusesIdsThatAreNotResultsOfTheRun() {
        assertThatThrownBy(() -> runCaseTools.reorderTestRunCases(run.key(), List.of(first.id())))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("not test case ids");
    }

    @Test
    void aCompletedRunCannotChangeItsCases() {
        testRunWriteTools.completeTestRun(run.key(), null, null);

        assertThatThrownBy(() -> runCaseTools.addTestCasesToRun(run.key(), List.of(second.id())))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("reopen it first");
    }
}
