package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.customField.CreateCustomFieldRequest;
import com.deanmanagement.testmanagement.project.internal.dto.customField.CustomFieldResponse;
import com.deanmanagement.testmanagement.project.internal.dto.customField.UpdateCustomFieldRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.shared.exception.ConflictException;
import com.deanmanagement.testmanagement.shared.exception.DuplicateKeyException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Definition rules of PRD-035 §3.2 and §4. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class CustomFieldServiceTest {

    @Autowired private CustomFieldService service;
    @Autowired private TestCaseService testCaseService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private EntityManager entityManager;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        Project project = new Project();
        project.setName("Custom fields");
        project.setKey("CFS");
        projectId = projectRepository.save(project).getId();
    }

    private CustomFieldResponse select(String name, String... options) {
        return service.create(projectId, new CreateCustomFieldRequest(CustomFieldEntityType.TEST_CASE, name,
                CustomFieldType.SELECT, List.of(options), null), null);
    }

    private CustomFieldResponse text(String name) {
        return service.create(projectId, new CreateCustomFieldRequest(CustomFieldEntityType.TEST_CASE, name,
                CustomFieldType.TEXT, null, null), null);
    }

    private TestCaseResponse caseWith(Map<String, Object> values) {
        return testCaseService.create(projectId, new CreateTestCaseRequest("Case", null, null, Priority.MEDIUM,
                TestCaseStatus.DRAFT, null, null, null, values), null);
    }

    private static UpdateCustomFieldRequest options(List<String> options, Map<String, String> renames) {
        return new UpdateCustomFieldRequest(null, null, options, renames, null, null);
    }

    @Nested
    class Create {

        @Test
        void appendsFieldsInOrder() {
            select("Component", "Checkout");
            text("Customer");

            assertThat(service.list(projectId, CustomFieldEntityType.TEST_CASE))
                    .extracting(CustomFieldResponse::name).containsExactly("Component", "Customer");
        }

        @Test
        void refusesANameThatDiffersOnlyByCase() {
            text("Component");

            assertThatThrownBy(() -> text(" component "))
                    .isInstanceOf(DuplicateKeyException.class);
        }

        @Test
        void allowsTheSameNameOnAnotherEntityType() {
            text("Component");

            CustomFieldResponse onRuns = service.create(projectId, new CreateCustomFieldRequest(
                    CustomFieldEntityType.TEST_RUN, "Component", CustomFieldType.TEXT, null, null), null);

            assertThat(onRuns.entityType()).isEqualTo(CustomFieldEntityType.TEST_RUN);
        }

        @Test
        void refusesASelectWithoutOptions() {
            assertThatThrownBy(() -> select("Component"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one option");
        }

        @Test
        void refusesDuplicateOptionsIgnoringCase() {
            assertThatThrownBy(() -> select("Component", "Checkout", "checkout"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate option");
        }

        @Test
        void refusesAnOptionContainingTheCsvSeparator() {
            assertThatThrownBy(() -> select("Component", "A;B"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void refusesMoreThanTheActiveFieldCap() {
            for (int i = 0; i < CustomFieldService.MAX_ACTIVE_FIELDS_PER_ENTITY_TYPE; i++) {
                text("Field " + i);
            }

            assertThatThrownBy(() -> text("One too many"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at most");
        }

        @Test
        void archivedFieldsDoNotCountTowardsTheCap() {
            for (int i = 0; i < CustomFieldService.MAX_ACTIVE_FIELDS_PER_ENTITY_TYPE; i++) {
                text("Field " + i);
            }
            service.archive(projectId, service.list(projectId, null).getFirst().id(), null);

            assertThat(text("Replacement").archived()).isFalse();
        }
    }

    @Nested
    class Update {

        @Test
        void refusesATypeChangeOnceTheFieldHoldsValues() {
            CustomFieldResponse field = text("Customer");
            caseWith(Map.of("Customer", "ACME"));

            assertThatThrownBy(() -> service.update(projectId, field.id(),
                    new UpdateCustomFieldRequest(null, CustomFieldType.NUMBER, null, null, null, null), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("type can't change");
        }

        @Test
        void allowsATypeChangeWhileTheFieldIsUnused() {
            CustomFieldResponse field = text("Customer");

            CustomFieldResponse changed = service.update(projectId, field.id(),
                    new UpdateCustomFieldRequest(null, CustomFieldType.SELECT, List.of("ACME"), null, null, null), null);

            assertThat(changed.fieldType()).isEqualTo(CustomFieldType.SELECT);
            assertThat(changed.options()).containsExactly("ACME");
        }

        @Test
        void refusesRemovingAnOptionInUse() {
            CustomFieldResponse field = select("Component", "Checkout", "Search");
            caseWith(Map.of("Component", "Search"));

            assertThatThrownBy(() -> service.update(projectId, field.id(), options(List.of("Checkout"), null), null))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Search");
        }

        @Test
        void removesAnUnusedOption() {
            CustomFieldResponse field = select("Component", "Checkout", "Search");
            caseWith(Map.of("Component", "Checkout"));

            CustomFieldResponse changed = service.update(projectId, field.id(), options(List.of("Checkout"), null), null);

            assertThat(changed.options()).containsExactly("Checkout");
        }

        @Test
        void renamingAnOptionRewritesStoredValues() {
            CustomFieldResponse field = select("Component", "Checkout", "Search");
            UUID caseId = caseWith(Map.of("Component", "Search")).id();

            service.update(projectId, field.id(), options(List.of("Checkout", "Find"), Map.of("Search", "Find")), null);
            entityManager.flush();
            entityManager.clear();

            assertThat(testCaseService.findById(projectId, caseId).customFields()).containsEntry("Component", "Find");
        }
    }

    @Nested
    class Delete {

        @Test
        void deletesAnUnusedField() {
            CustomFieldResponse field = text("Customer");

            service.delete(projectId, field.id(), null);

            assertThat(service.list(projectId, null)).isEmpty();
        }

        @Test
        void refusesAFieldThatHoldsValues() {
            CustomFieldResponse field = text("Customer");
            caseWith(Map.of("Customer", "ACME"));

            assertThatThrownBy(() -> service.delete(projectId, field.id(), null))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void forceDiscardsTheValues() {
            CustomFieldResponse field = text("Customer");
            UUID caseId = caseWith(Map.of("Customer", "ACME")).id();
            entityManager.flush();
            entityManager.clear();

            service.forceDelete(projectId, field.id(), null);
            entityManager.flush();
            entityManager.clear();

            assertThat(testCaseService.findById(projectId, caseId).customFields()).isEmpty();
        }
    }
}
