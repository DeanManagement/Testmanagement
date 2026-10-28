package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.requirement.TraceabilityRowResponse.CoverageStatus;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.RequirementStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.service.RequirementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TM18: requirements nest, coverage rolls up from the leaves; committed (not @Transactional). */
class McpRequirementHierarchyApiTest extends McpToolApiTestSupport {

    @Autowired private RequirementTools requirementTools;
    @Autowired private RequirementService requirementService;

    private UUID requirement(String externalId, String parent) {
        return requirementTools.createRequirement(externalId, externalId, null, null, null, null, parent).id();
    }

    private String coverageOf(String externalId) {
        return requirementTools.getTraceabilityMatrix(null).requirements().stream()
                .filter(row -> row.externalId().equals(externalId))
                .findFirst().orElseThrow().coverage();
    }

    @Test
    void aParentIsCoveredOnlyWhenEveryLeafPassed() {
        authenticateAs(project, ProjectRole.TESTER);
        requirement("EPIC-1", null);
        UUID login = requirement("STORY-1", "EPIC-1");
        UUID logout = requirement("STORY-2", "EPIC-1");
        McpDtos.CreatedTestCase loginCase = createCase("Log in");
        McpDtos.CreatedTestCase logoutCase = createCase("Log out");
        requirementTools.linkTestCasesToRequirement(login, List.of(loginCase.id()));
        requirementTools.linkTestCasesToRequirement(logout, List.of(logoutCase.id()));
        McpDtos.CreatedTestRun run = runOf(loginCase, logoutCase);
        resultRecordingTools.recordTestResult(run.key(), TestResultStatus.PASSED, loginCase.id(), null, null, null, null, null);
        testRunWriteTools.completeTestRun(run.key(), null, null);

        assertThat(coverageOf("STORY-1")).isEqualTo(CoverageStatus.PASSED.name());
        assertThat(coverageOf("EPIC-1")).isNotEqualTo(CoverageStatus.PASSED.name());

        requirementTools.updateRequirement(logout, null, null, null, RequirementStatus.OBSOLETE, null, null, null);

        assertThat(coverageOf("EPIC-1")).isEqualTo(CoverageStatus.PASSED.name());
    }

    @Test
    void coverageCountsLeavesNotParents() {
        authenticateAs(project, ProjectRole.TESTER);
        requirement("EPIC-1", null);
        requirement("STORY-1", "EPIC-1");
        requirement("STORY-2", "EPIC-1");

        assertThat(requirementService.coverage(project.getId()).totalRequirements()).isEqualTo(2);
    }

    @Test
    void aRequirementCannotMoveBelowItself() {
        authenticateAs(project, ProjectRole.TESTER);
        UUID epic = requirement("EPIC-1", null);
        requirement("STORY-1", "EPIC-1");
        requirement("TASK-1", "STORY-1");

        assertThatThrownBy(() -> requirementTools.updateRequirement(epic, null, null, null, null, null, null, "TASK-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requirementTools.updateRequirement(epic, null, null, null, null, null, null, "EPIC-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deletingAParentMovesItsChildrenUpALevel() {
        authenticateAs(project, ProjectRole.TESTER);
        UUID epic = requirement("EPIC-1", null);
        UUID story = requirement("STORY-1", "EPIC-1");
        requirement("TASK-1", "STORY-1");

        requirementTools.deleteRequirement("STORY-1");

        assertThat(requirementTools.getRequirement("TASK-1").parentId()).isEqualTo(epic);
    }

    @Test
    void anEmptyParentMovesARequirementToTheTop() {
        authenticateAs(project, ProjectRole.TESTER);
        requirement("EPIC-1", null);
        UUID story = requirement("STORY-1", "EPIC-1");

        requirementTools.updateRequirement(story, null, null, null, null, null, null, "");

        assertThat(requirementTools.getRequirement("STORY-1").parentId()).isNull();
    }
}
