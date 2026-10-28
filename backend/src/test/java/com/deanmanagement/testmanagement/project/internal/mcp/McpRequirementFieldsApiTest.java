package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.customField.CreateCustomFieldRequest;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.RequirementStatus;
import com.deanmanagement.testmanagement.project.internal.service.CustomFieldService;
import com.deanmanagement.testmanagement.project.internal.service.RequirementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TM18: requirement status, priority and custom fields, committed (not @Transactional). */
class McpRequirementFieldsApiTest extends McpToolApiTestSupport {

    @Autowired private RequirementTools requirementTools;
    @Autowired private CustomFieldService customFieldService;
    @Autowired private RequirementService requirementService;

    @Test
    void aNewRequirementIsADraftOfMediumPriority() {
        authenticateAs(project, ProjectRole.TESTER);

        McpDtos.Requirement created = requirementTools.createRequirement("REQ-1", "Log in", null, null, null, null, null);

        assertThat(created.status()).isEqualTo(RequirementStatus.DRAFT);
        assertThat(created.priority()).isEqualTo(Priority.MEDIUM);
    }

    @Test
    void statusPriorityAndCustomFieldsAreStoredAndUpdatedSeparately() {
        customFieldService.create(project.getId(), new CreateCustomFieldRequest(CustomFieldEntityType.REQUIREMENT,
                "Source", CustomFieldType.SELECT, List.of("Customer", "Regulator"), false), null);
        authenticateAs(project, ProjectRole.TESTER);
        UUID id = requirementTools.createRequirement("REQ-1", "Log in", null, RequirementStatus.APPROVED,
                Priority.HIGH, Map.of("source", "regulator"), null).id();

        McpDtos.Requirement updated = requirementTools.updateRequirement(id, null, null, null,
                RequirementStatus.OBSOLETE, null, null, null);

        assertThat(updated.status()).isEqualTo(RequirementStatus.OBSOLETE);
        assertThat(updated.priority()).isEqualTo(Priority.HIGH);
        assertThat(requirementTools.getRequirement("REQ-1").customFields()).isEqualTo(Map.of("Source", "Regulator"));
    }

    @Test
    void anUnknownCustomFieldIsRefused() {
        authenticateAs(project, ProjectRole.TESTER);

        assertThatThrownBy(() -> requirementTools.createRequirement("REQ-1", "Log in", null, null, null,
                Map.of("Nope", "x"), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anObsoleteRequirementDoesNotCountTowardsCoverage() {
        authenticateAs(project, ProjectRole.TESTER);
        requirementTools.createRequirement("REQ-1", "Live", null, null, null, null, null);
        requirementTools.createRequirement("REQ-2", "Gone", null, RequirementStatus.OBSOLETE, null, null, null);

        assertThat(requirementService.coverage(project.getId()).totalRequirements()).isEqualTo(1);
        assertThat(requirementTools.getTraceabilityMatrix(null).requirements()).hasSize(2);
    }
}
