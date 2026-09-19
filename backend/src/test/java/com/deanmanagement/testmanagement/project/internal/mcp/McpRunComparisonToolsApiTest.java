package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.comparison.RunComparisonResponse;
import com.deanmanagement.testmanagement.project.internal.dto.comparison.RunComparisonResponse.Category;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.service.RunComparisonService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** An agent can say "the build broke these tests" (PRD-038 §3.6). */
class McpRunComparisonToolsApiTest extends McpToolApiTestSupport {

    @Autowired
    private RunComparisonService runComparisonService;

    @Test
    void comparesRunsNamedByKeyAndMatchesRest() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase pay = createCase("Pay");
        McpDtos.CreatedTestCase browse = createCase("Browse");
        McpDtos.CreatedTestRun base = runOf(pay, browse);
        resultRecordingTools.recordTestResult(base.key(), TestResultStatus.PASSED, pay.id(), null, null, null, null, null);
        resultRecordingTools.recordTestResult(base.key(), TestResultStatus.PASSED, browse.id(), null, null, null, null, null);
        McpDtos.CreatedTestRun head = runOf(pay, browse);
        resultRecordingTools.recordTestResult(head.key(), TestResultStatus.FAILED, pay.id(), null, "boom", null, null, null);
        resultRecordingTools.recordTestResult(head.key(), TestResultStatus.PASSED, browse.id(), null, null, null, null, null);

        RunComparisonResponse viaMcp = testRunReadTools.compareTestRuns(head.key(), base.key());

        assertThat(viaMcp.rows()).extracting(RunComparisonResponse.Row::category, RunComparisonResponse.Row::title)
                .containsExactly(tuple(Category.NEWLY_FAILING, "Pay"));
        assertThat(viaMcp).usingRecursiveComparison().isEqualTo(runComparisonService.compare(project.getId(),
                head.id(), base.id(), RunComparisonService.ComparisonRows.CHANGES_ONLY));
    }

    @Test
    void omittingTheBaseComparesWithThePreviousRunOfTheSameName() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase pay = createCase("Pay");
        McpDtos.CreatedTestRun base = runOf(pay);
        McpDtos.CreatedTestRun head = runOf(pay);

        RunComparisonResponse comparison = testRunReadTools.compareTestRuns(head.key(), null);

        assertThat(comparison.baseAutoSelected()).isTrue();
        assertThat(comparison.base().id()).isEqualTo(base.id());
    }

    @Test
    void aRunKeyOfAnotherProjectIsUnknown() {
        authenticateAs(otherProject, ProjectRole.TESTER);
        McpDtos.CreatedTestRun foreign = runOf(createCase("Theirs"));
        authenticateAs(project, ProjectRole.VIEWER);

        assertThatThrownBy(() -> testRunReadTools.compareTestRuns(foreign.key(), null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("No test run");
    }
}
