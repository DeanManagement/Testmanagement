package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportResponse;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.CreateBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.dto.customField.CreateCustomFieldRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CloneTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Value validation and storage of PRD-035 §3.2–3.3 and §4, through the entity write paths. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class CustomFieldValuesTest {

    @Autowired private CustomFieldService fieldService;
    @Autowired private TestCaseService testCaseService;
    @Autowired private TestRunService testRunService;
    @Autowired private BugReportService bugReportService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        Project project = new Project();
        project.setName("Values");
        project.setKey("CFV");
        project.setBugReportsEnabled(true);
        projectId = projectRepository.save(project).getId();
        field(CustomFieldEntityType.TEST_CASE, "Notes", CustomFieldType.TEXT);
        field(CustomFieldEntityType.TEST_CASE, "Effort", CustomFieldType.NUMBER);
        field(CustomFieldEntityType.TEST_CASE, "Due", CustomFieldType.DATE);
        field(CustomFieldEntityType.TEST_CASE, "Component", CustomFieldType.SELECT, "Checkout", "Search");
        field(CustomFieldEntityType.TEST_CASE, "Browsers", CustomFieldType.MULTI_SELECT, "Chrome", "Firefox", "Safari");
    }

    private UUID field(CustomFieldEntityType entityType, String name, CustomFieldType type, String... options) {
        return fieldService.create(projectId, new CreateCustomFieldRequest(entityType, name, type,
                List.of(options), null), null).id();
    }

    private UUID requiredField(CustomFieldEntityType entityType, String name) {
        return fieldService.create(projectId, new CreateCustomFieldRequest(entityType, name, CustomFieldType.TEXT,
                null, true), null).id();
    }

    /**
     * Flushed, as a separate request would be: before its first flush a new entity's value list is
     * a plain list whose removals Hibernate can't see.
     */
    private TestCaseResponse createCase(Map<String, Object> values) {
        TestCaseResponse created = testCaseService.create(projectId, caseRequest(values), null);
        entityManager.flush();
        return created;
    }

    private static CreateTestCaseRequest caseRequest(Map<String, Object> values) {
        return new CreateTestCaseRequest("Case", null, null, Priority.MEDIUM, TestCaseStatus.DRAFT,
                null, null, null, values);
    }

    private TestCaseResponse reload(UUID caseId) {
        entityManager.flush();
        entityManager.clear();
        return testCaseService.findById(projectId, caseId);
    }

    private static UpdateTestCaseRequest valuesOnly(Map<String, Object> values) {
        return new UpdateTestCaseRequest(null, null, null, null, null, null, null, values);
    }

    @Nested
    class Types {

        @Test
        void storesEachTypeAndReadsItBackInDisplayOrder() {
            UUID id = createCase(Map.of("Notes", " ACME ", "Effort", "12.50", "Due", "2026-10-01",
                    "Component", "Search", "Browsers", List.of("safari", "Chrome"))).id();

            Map<String, Object> values = reload(id).customFields();

            assertThat(values).containsExactly(
                    Map.entry("Notes", "ACME"),
                    Map.entry("Effort", new BigDecimal("12.5")),
                    Map.entry("Due", "2026-10-01"),
                    Map.entry("Component", "Search"),
                    Map.entry("Browsers", List.of("Chrome", "Safari")));
        }

        @Test
        void refusesTextOverFiveHundredCharacters() {
            assertThatThrownBy(() -> createCase(Map.of("Notes", "x".repeat(501))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("500");
        }

        @Test
        void refusesANonNumber() {
            assertThatThrownBy(() -> createCase(Map.of("Effort", "lots")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expects a number");
        }

        @Test
        void refusesMoreThanFourDecimalPlacesRatherThanRounding() {
            assertThatThrownBy(() -> createCase(Map.of("Effort", 1.23456)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("decimal places");
        }

        @Test
        void refusesADateInAnotherFormat() {
            assertThatThrownBy(() -> createCase(Map.of("Due", "01.10.2026")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("yyyy-MM-dd");
        }

        @Test
        void refusesAnUnknownOption() {
            assertThatThrownBy(() -> createCase(Map.of("Component", "Payments")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Options: Checkout, Search");
        }

        @Test
        void refusesAnUnknownOptionInAMultiSelect() {
            assertThatThrownBy(() -> createCase(Map.of("Browsers", List.of("Chrome", "Edge"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Edge");
        }

        @Test
        void refusesAListForASingleSelect() {
            assertThatThrownBy(() -> createCase(Map.of("Component", List.of("Search"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expects text");
        }

        @Test
        void refusesAnUnknownFieldName() {
            assertThatThrownBy(() -> createCase(Map.of("Sprint", "12")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown custom field(s): Sprint");
        }
    }

    @Nested
    class Updates {

        @Test
        void anAbsentMapLeavesValuesAlone() {
            UUID id = createCase(Map.of("Notes", "ACME")).id();

            testCaseService.update(projectId, id, new UpdateTestCaseRequest("Renamed", null, null, null, null,
                    null, null), null);

            assertThat(reload(id).customFields()).containsEntry("Notes", "ACME");
        }

        @Test
        void anAbsentKeyLeavesThatFieldAlone() {
            UUID id = createCase(Map.of("Notes", "ACME", "Component", "Search")).id();

            testCaseService.update(projectId, id, valuesOnly(Map.of("Component", "Checkout")), null);

            assertThat(reload(id).customFields())
                    .containsEntry("Notes", "ACME").containsEntry("Component", "Checkout");
        }

        @Test
        void aNullValueClearsThatField() {
            UUID id = createCase(Map.of("Notes", "ACME", "Browsers", List.of("Chrome", "Firefox"))).id();
            Map<String, Object> clear = new HashMap<>();
            clear.put("Browsers", null);

            testCaseService.update(projectId, id, valuesOnly(clear), null);

            assertThat(reload(id).customFields()).containsOnlyKeys("Notes");
        }

        @Test
        void anInvalidValueLeavesEveryFieldUnchanged() {
            UUID id = createCase(Map.of("Notes", "ACME")).id();

            assertThatThrownBy(() -> testCaseService.update(projectId, id,
                    valuesOnly(Map.of("Notes", "Other", "Effort", "lots")), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Required {

        @Test
        void anInteractiveCreateMustFillIt() {
            requiredField(CustomFieldEntityType.TEST_CASE, "Owner");

            assertThatThrownBy(() -> createCase(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Required custom field(s) missing: Owner");
        }

        @Test
        void aMachineCreateIsNeverBlocked() {
            requiredField(CustomFieldEntityType.TEST_CASE, "Owner");

            TestCaseResponse created = testCaseService.create(projectId, caseRequest(null), null,
                    CustomFieldWriteMode.MACHINE);

            assertThat(created.customFields()).isEmpty();
        }

        @Test
        void anEditThatDoesNotTouchCustomFieldsIsNotBlockedByAFieldAddedLater() {
            UUID id = createCase(null).id();
            requiredField(CustomFieldEntityType.TEST_CASE, "Owner");

            TestCaseResponse updated = testCaseService.update(projectId, id,
                    new UpdateTestCaseRequest("Renamed", null, null, null, null, null, null), null);

            assertThat(updated.title()).isEqualTo("Renamed");
        }

        @Test
        void anEditThatSendsCustomFieldsMustFillIt() {
            UUID id = createCase(null).id();
            requiredField(CustomFieldEntityType.TEST_CASE, "Owner");

            assertThatThrownBy(() -> testCaseService.update(projectId, id, valuesOnly(Map.of("Notes", "x")), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Owner");
        }

        @Test
        void anArchivedRequiredFieldIsNotEnforced() {
            UUID owner = requiredField(CustomFieldEntityType.TEST_CASE, "Owner");
            fieldService.archive(projectId, owner, null);

            assertThat(createCase(null).customFields()).isEmpty();
        }
    }

    @Nested
    class RunsAndBugs {

        @Test
        void aRunStoresValuesAndACloneCopiesThem() {
            field(CustomFieldEntityType.TEST_RUN, "Sprint", CustomFieldType.NUMBER);
            TestRunResponse run = testRunService.create(projectId, new CreateTestRunRequest("Run", null, null, null,
                    null, null, null, Map.of("Sprint", 12)), null);

            TestRunResponse clone = testRunService.cloneRun(projectId, run.id(),
                    new CloneTestRunRequest("Clone", null, null), null);

            assertThat(clone.customFields()).containsEntry("Sprint", new BigDecimal("12"));
        }

        @Test
        void aCloneIsNotBlockedByAFieldThatBecameRequired() {
            TestRunResponse run = testRunService.create(projectId, new CreateTestRunRequest("Run", null, null,
                    null, null), null);
            requiredField(CustomFieldEntityType.TEST_RUN, "Owner");

            TestRunResponse clone = testRunService.cloneRun(projectId, run.id(),
                    new CloneTestRunRequest("Clone", null, null), null);

            assertThat(clone.name()).isEqualTo("Clone");
        }

        @Test
        void aBugReportStoresValues() {
            field(CustomFieldEntityType.BUG_REPORT, "Customer", CustomFieldType.TEXT);

            BugReportResponse bug = bugReportService.create(projectId, new CreateBugReportRequest("Broken", null,
                    null, null, null, Priority.HIGH, null, null, null, null, null, null,
                    Map.of("Customer", "ACME"), null), null);

            assertThat(bug.customFields()).containsEntry("Customer", "ACME");
        }

        @Test
        void aTestCaseFieldIsUnknownOnARun() {
            assertThatThrownBy(() -> testRunService.create(projectId, new CreateTestRunRequest("Run", null, null,
                    null, null, null, null, Map.of("Notes", "x")), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown custom field");
        }
    }

    @Nested
    class Storage {

        @Test
        void deletingATestCaseDeletesItsValues() {
            UUID id = createCase(Map.of("Notes", "ACME")).id();
            entityManager.flush();

            testCaseService.delete(projectId, id, null);
            entityManager.flush();

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM custom_field_values", Long.class)).isZero();
        }

        @Test
        void theDatabaseRefusesAValueWithNoOwner() {
            UUID fieldId = fieldService.list(projectId, CustomFieldEntityType.TEST_CASE).getFirst().id();

            assertThatThrownBy(() -> jdbc.update("INSERT INTO custom_field_values (id, field_id, value_text, "
                    + "created_at, updated_at) VALUES (?, ?, 'x', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    UUID.randomUUID(), fieldId))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void theDatabaseRefusesAValueWithTwoOwners() {
            UUID fieldId = fieldService.list(projectId, CustomFieldEntityType.TEST_CASE).getFirst().id();
            UUID caseId = createCase(null).id();
            UUID runId = testRunService.create(projectId, new CreateTestRunRequest("Run", null, null, null,
                    null), null).id();
            entityManager.flush();

            assertThatThrownBy(() -> jdbc.update("INSERT INTO custom_field_values (id, field_id, test_case_id, "
                    + "test_run_id, value_text, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, 'x', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    UUID.randomUUID(), fieldId, caseId, runId))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
