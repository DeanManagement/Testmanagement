package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.customField.CreateCustomFieldRequest;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.CustomFieldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Agents read and write custom fields by name (PRD-035 §3.8). */
class McpCustomFieldToolsApiTest extends McpToolApiTestSupport {

    @Autowired
    private ProjectDiscoveryTools discoveryTools;
    @Autowired
    private CustomFieldService fieldService;

    @BeforeEach
    void defineFields() {
        define(project, "Component", CustomFieldType.SELECT, false, "Checkout", "Search");
        define(project, "Owner", CustomFieldType.TEXT, true);
        define(otherProject, "Secret", CustomFieldType.TEXT, false);
    }

    private void define(Project target, String name, CustomFieldType type, boolean required, String... options) {
        fieldService.create(target.getId(), new CreateCustomFieldRequest(CustomFieldEntityType.TEST_CASE, name, type,
                List.of(options), required), null);
    }

    private McpDtos.CreatedTestCase createWith(String title, Map<String, Object> customFields) {
        return testCaseTools.createTestCase(title, Priority.MEDIUM, null, null, null, null, null, null,
                customFields, null, null);
    }

    @Test
    void listCustomFieldsShowsOnlyTheKeysProject() {
        authenticateAs(project, ProjectRole.VIEWER);

        assertThat(discoveryTools.listCustomFields(null))
                .extracting(McpDtos.CustomField::name).containsExactly("Component", "Owner");
    }

    @Test
    void createAndGetUseFieldNames() {
        authenticateAs(project, ProjectRole.TESTER);

        McpDtos.CreatedTestCase created = createWith("Pay", Map.of("component", "search"));

        assertThat(testCaseTools.getTestCase(created.key()).customFields()).containsEntry("Component", "Search");
    }

    @Test
    void anAgentIsNotBlockedByARequiredField() {
        authenticateAs(project, ProjectRole.TESTER);

        McpDtos.CreatedTestCase created = createWith("Pay", null);
        testCaseTools.updateTestCase(created.key(), null, null, null, null, null, null, null, null,
                Map.of("Component", "Checkout"), null);

        assertThat(testCaseTools.getTestCase(created.key()).customFields()).containsOnlyKeys("Component");
    }

    @Test
    void anUnknownNameIsRefusedWithTheValidNames() {
        authenticateAs(project, ProjectRole.TESTER);

        assertThatThrownBy(() -> createWith("Pay", Map.of("Secret", "x")))
                .hasMessageContaining("Unknown custom field(s): Secret")
                .hasMessageContaining("Component, Owner");
    }

    @Test
    void searchFiltersByCustomFields() {
        authenticateAs(project, ProjectRole.TESTER);
        createWith("Pay", Map.of("Component", "Checkout"));
        createWith("Find", Map.of("Component", "Search"));

        McpDtos.TestCasePage page = testCaseTools.searchTestCases(null, null, null, null, null, null,
                Map.of("Component", List.of("Search")), null, null);

        assertThat(page.testCases()).extracting(McpDtos.TestCaseSummary::title).containsExactly("Find");
    }
}
