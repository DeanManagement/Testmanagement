package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Finding requirements, and getting from a test case to its requirements (TM08, TM16). */
class McpRequirementLookupApiTest extends McpToolApiTestSupport {

    @Autowired private RequirementTools requirementTools;

    @Test
    void aRequirementIsFoundByExternalIdOrUuid() {
        UUID id = requirementOf(project, "REQ-14", "Checkout totals include VAT");
        authenticateAs(project, ProjectRole.VIEWER);

        assertThat(requirementTools.getRequirement("REQ-14").id()).isEqualTo(id);
        assertThat(requirementTools.getRequirement(id.toString()).externalId()).isEqualTo("REQ-14");
    }

    @Test
    void anotherProjectsRequirementIsNotFound() {
        requirementOf(otherProject, "REQ-9", "Foreign");
        authenticateAs(project, ProjectRole.VIEWER);

        assertThatThrownBy(() -> requirementTools.getRequirement("REQ-9"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listRequirementsMatchesTheQueryOnExternalIdOrTitle() {
        requirementOf(project, "REQ-1", "Users can log in");
        requirementOf(project, "REQ-2", "Checkout totals include VAT");
        requirementOf(project, "SEC-7", "Passwords are hashed");
        authenticateAs(project, ProjectRole.VIEWER);

        assertThat(requirementTools.listRequirements(null, null, "req-").requirements())
                .extracting(McpDtos.Requirement::externalId).containsExactly("REQ-1", "REQ-2");
        assertThat(requirementTools.listRequirements(null, null, "VAT").requirements())
                .extracting(McpDtos.Requirement::externalId).containsExactly("REQ-2");
    }

    @Test
    void getTestCaseNamesTheRequirementsItIsLinkedTo() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        UUID id = requirementTools.createRequirement("REQ-1", "Users can log in", null).id();
        requirementTools.linkTestCasesToRequirement(id, List.of(login.id()));

        assertThat(testCaseTools.getTestCase(login.key()).requirements())
                .containsExactly(new McpDtos.RequirementRef(id, "REQ-1", "Users can log in"));
    }

    @Test
    void searchTestCasesFiltersByRequirement() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        createCase("Logout");
        UUID id = requirementTools.createRequirement("REQ-1", "Users can log in", null).id();
        requirementTools.linkTestCasesToRequirement(id, List.of(login.id()));

        assertThat(testCaseTools.searchTestCases(null, null, null, null, null, null, null, null, null,
                "REQ-1").testCases())
                .extracting(McpDtos.TestCaseSummary::id).containsExactly(login.id());
    }

    private UUID requirementOf(com.deanmanagement.testmanagement.project.internal.entity.Project target,
                               String externalId, String title) {
        authenticateAs(target, ProjectRole.TESTER);
        return requirementTools.createRequirement(externalId, title, null).id();
    }
}
