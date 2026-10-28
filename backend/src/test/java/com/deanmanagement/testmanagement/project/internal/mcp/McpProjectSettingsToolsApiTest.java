package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldType;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TM34/TM35: environments and custom fields over MCP, for ADMIN keys only. Not transactional. */
class McpProjectSettingsToolsApiTest extends McpToolApiTestSupport {

    @Autowired private ProjectSettingsTools settingsTools;

    @Test
    void aTesterKeyCannotChangeProjectSettings() {
        authenticateAs(project, ProjectRole.TESTER);

        assertThatThrownBy(() -> settingsTools.createEnvironment("qa", null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("requires ADMIN");
    }

    @Test
    void anAdminKeyCreatesRenamesAndArchivesAnEnvironment() {
        authenticateAs(project, ProjectRole.ADMIN);
        settingsTools.createEnvironment("qa", "Quality assurance");

        settingsTools.updateEnvironment("QA", "qa-eu", null, null);
        settingsTools.updateEnvironment("qa-eu", null, null, true);

        assertThat(testRunReadTools.listEnvironments().environments())
                .extracting(McpDtos.Environment::name)
                .doesNotContain("qa", "qa-eu");
        assertThat(environmentService.list(project.getId(), true))
                .anySatisfy(e -> {
                    assertThat(e.name()).isEqualTo("qa-eu");
                    assertThat(e.archived()).isTrue();
                });
    }

    @Test
    void anAdminKeyManagesACustomField() {
        authenticateAs(project, ProjectRole.ADMIN);
        settingsTools.createCustomField(CustomFieldEntityType.TEST_CASE, "Component", CustomFieldType.SELECT,
                List.of("UI", "API"), null);

        McpDtos.CustomField field = settingsTools.updateCustomField(CustomFieldEntityType.TEST_CASE, "Component",
                "Area", List.of("UI", "API", "DB"), true, null);
        McpDtos.CustomField archived = settingsTools.updateCustomField(CustomFieldEntityType.TEST_CASE, "Area",
                null, null, null, true);

        assertThat(field.name()).isEqualTo("Area");
        assertThat(field.options()).containsExactly("UI", "API", "DB");
        assertThat(field.required()).isTrue();
        assertThat(archived.archived()).isTrue();
    }

    @Test
    void anUnknownFieldNamesTheKnownOnes() {
        authenticateAs(project, ProjectRole.ADMIN);
        settingsTools.createCustomField(CustomFieldEntityType.BUG_REPORT, "Browser", CustomFieldType.TEXT, null, null);

        assertThatThrownBy(() -> settingsTools.updateCustomField(CustomFieldEntityType.BUG_REPORT, "Brwoser",
                null, null, true, null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("\"Browser\"");
    }
}
